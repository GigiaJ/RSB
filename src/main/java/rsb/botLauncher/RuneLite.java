/**
 * Author:GigiaJ
 */
package rsb.botLauncher;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

import java.applet.Applet;
import java.awt.*;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Constructor;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.Nullable;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.swing.*;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import joptsimple.*;
import joptsimple.util.EnumConverter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.client.ClientSessionManager;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.account.SessionManager;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.CommandManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.discord.DiscordService;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.game.FriendChatManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.LootManager;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.rs.ClientLoader;
import net.runelite.client.rs.ClientUpdateCheckMode;
import rsb.event.EventManager;
import rsb.event.events.PaintEvent;
import rsb.event.events.TextPaintEvent;
import rsb.gui.AccountManager;
import rsb.internal.BreakHandler;
import rsb.internal.PassiveScriptHandler;
import rsb.internal.ScriptHandler;
import rsb.methods.*;
import rsb.internal.InputManager;
import rsb.internal.BotHooks;
import rsb.internal.BotModule;
import rsb.internal.input.Canvas;
import rsb.plugin.Botplugin;
import rsb.util.OutputObjectComparer;
import rsb.util.Parameters;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.FatalErrorDialog;
import net.runelite.client.ui.SplashScreen;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayRenderer;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxOverlay;
import net.runelite.client.ui.overlay.tooltip.TooltipOverlay;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;
import net.runelite.client.ws.PartyService;
import net.runelite.http.api.RuneLiteAPI;
import okhttp3.Cache;
import okhttp3.OkHttpClient;


@Singleton
@Slf4j
public class RuneLite extends net.runelite.client.RuneLite {
    public static final File RUNELITE_DIR = new File(System.getProperty("user.home"), ".runelite");
    public static final File CACHE_DIR = new File(RUNELITE_DIR, "cache");
    public static final File PLUGINS_DIR = new File(RUNELITE_DIR, "plugins");
    public static final File PROFILES_DIR = new File(RUNELITE_DIR, "profiles");
    public static final File SCREENSHOT_DIR = new File(RUNELITE_DIR, "screenshots");
    public static final File LOGS_DIR = new File(RUNELITE_DIR, "logs");
    public static final File DEFAULT_SESSION_FILE = new File(RUNELITE_DIR, "session");
    public static final File DEFAULT_CONFIG_FILE = new File(RUNELITE_DIR, "settings.properties");
    private static final int MAX_OKHTTP_CACHE_SIZE = 20 * 1024 * 1024; // 20mb

    @Getter
    private static Injector injector;

    @Inject
    private PluginManager pluginManager;

    @Inject
    private ExternalPluginManager externalPluginManager;

    @Inject
    private EventBus eventBus;

    @Inject
    private ConfigManager configManager;

    @Inject
    private DrawManager drawManager;

    @Inject
    private SessionManager sessionManager;

    @Inject
    private DiscordService discordService;

    @Inject
    private ClientSessionManager clientSessionManager;

    @Inject
    public ClientUI clientUI;

    @Inject
    private Provider<InfoBoxManager> infoBoxManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private Provider<PartyService> partyService;

    @Inject
    private Provider<ItemManager> itemManager;

    @Inject
    private Provider<OverlayRenderer> overlayRenderer;

    @Inject
    private Provider<FriendChatManager> friendsChatManager;

    @Inject
    private Provider<ChatMessageManager> chatMessageManager;

    @Inject
    private Provider<MenuManager> menuManager;

    @Inject
    private Provider<CommandManager> commandManager;

    @Inject
    private Provider<TooltipOverlay> tooltipOverlay;

    @Inject
    private Provider<WorldMapOverlay> worldMapOverlay;

    @Inject
    private Provider<LootManager> lootManager;

    @Inject
    private Provider<ChatboxPanelManager> chatboxPanelManager;

    @Inject
    private Provider<BotHooks> hooks;

    @Inject
    @Nullable
    private Client client;

    private String account;
    private MethodContext methods;
    private Component panel;
    private PaintEvent paintEvent;
    private TextPaintEvent textPaintEvent;
    private EventManager eventManager;
    private BufferedImage backBuffer;
    private BufferedImage image;
    private InputManager im;
    private ScriptHandler sh;
    private PassiveScriptHandler psh;
    private BreakHandler bh;
    private Map<String, EventListener> listeners;
    private boolean kill_passive = false;
    private Canvas canvas;

    /**
     * Defines what types of input are enabled when overrideInput is false.
     * Defaults to 'keyboard only' whenever a script is started.
     */
    public volatile int inputFlags = Environment.INPUT_KEYBOARD | Environment.INPUT_MOUSE;

    /**
     * Whether or not user input is allowed despite a script's preference.
     */
    public volatile boolean overrideInput = false;

    /**
     * Whether or not all anti-randoms are enabled.
     */
    public volatile boolean disableRandoms = false;

    /**
     * Whether or not the login screen anti-random is enabled.
     */
    public volatile boolean disableAutoLogin = false;

    /**
     * Whether or not rendering is enabled.
     */
    public volatile boolean disableRendering = false;

    /**
     * Whether or not the canvas is enabled.
     */
    public volatile boolean disableCanvas = false;


    public void changeCanvasState() {
        if (disableCanvas) {
            getLoader().setVisible(false);
            return;
        }
        getLoader().setVisible(true);
    }

    public String getAccountName() {
        return account;
    }

    public Client getClient() {
        return client = injector.getInstance(Client.class);
    }

    public ItemManager getItemManager() { return injector.getInstance(ItemManager.class);}

    public MethodContext getMethodContext() {
        return methods;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public InputManager getInputManager() {
        return im;
    }

    public BreakHandler getBreakHandler() {
        return bh;
    }

    public ScriptHandler getScriptHandler() {
        return sh;
    }

    public PassiveScriptHandler getPassiveScriptHandler() {
        return psh;
    }

    public void addListener(Class<?> clazz) {
        EventListener el = instantiateListener(clazz);
        listeners.put(clazz.getName(), el);
        eventManager.addListener(el);
    }

    public void removeListener(Class<?> clazz) {
        EventListener el = listeners.get(clazz.getName());
        listeners.remove(clazz.getName());
        eventManager.removeListener(el);
    }

    private EventListener instantiateListener(Class<?> clazz) {
        try {
            EventListener listener;
            try {
                Constructor<?> constructor = clazz.getConstructor(RuneLite.class);
                listener = (EventListener) constructor.newInstance(this);
            } catch (Exception e) {
                listener = clazz.asSubclass(EventListener.class).newInstance();
            }
            return listener;
        } catch (Exception ignored) {
            log.debug("Failed to instantiate listener", ignored);
        }
        return null;
    }

    public boolean hasListener(Class<?> clazz) {
        return clazz != null && listeners.get(clazz.getName()) != null;
    }

    public BufferedImage getImage() {
        return image;
    }

    public Component getPanel() {
        return this.panel;
    }

    public void setPanel(Component c) {
        this.panel = c;
    }

    public boolean setAccount(final String name) {
        boolean exist = false;
        for (String s : AccountManager.getAccountNames()) {
            if (s.toLowerCase().equals(name.toLowerCase())) {
                exist = true;
            }
        }
        if (exist) {
            account = name;
            return true;
        }
        account = null;
        return false;
    }

    public Canvas getCanvas() {
        if (client == null) {
            return null;
        }
        if (client.getCanvas() == null) {
            return null;
        }
        if (canvas == null) {
            canvas = new Canvas(client.getCanvas());
            return canvas;
        }
        return canvas;
    }

    public Graphics getBufferGraphics(MainBufferProvider mainBufferProvider) {
        Graphics back = mainBufferProvider.getImage().getGraphics();
        paintEvent.graphics = back;
        textPaintEvent.graphics = back;
        textPaintEvent.idx = 0;
        eventManager.processEvent(paintEvent);
        eventManager.processEvent(textPaintEvent);
        back.dispose();
        back.drawImage(backBuffer, 0, 0, null);
        return back;
    }

    public Applet getLoader() {
        return (Applet) this.getClient();
    }

    public RuneLite() {
    }

    public static void launch(String[] args) throws Exception {
        Locale.setDefault(Locale.ENGLISH);

        final OptionParser parser = new OptionParser();
        parser.accepts("developer-mode", "Enable developer tools");
        parser.accepts("debug", "Show extra debugging output");
        parser.accepts("bot", "Starts the client in bot mode");
        parser.accepts("bot-runelite", "Starts the client in Bot RuneLite mode");

        final ArgumentAcceptingOptionSpec<File> sessionfile = parser.accepts("sessionfile", "Use a specified session file")
                .withRequiredArg()
                .withValuesConvertedBy(new ConfigFileConverter())
                .defaultsTo(DEFAULT_SESSION_FILE);

        final ArgumentAcceptingOptionSpec<File> configfile = parser.accepts("config", "Use a specified config file")
                .withRequiredArg()
                .withValuesConvertedBy(new ConfigFileConverter())
                .defaultsTo(DEFAULT_CONFIG_FILE);

        final ArgumentAcceptingOptionSpec<ClientUpdateCheckMode> updateMode = parser
                .accepts("rs", "Select client type")
                .withRequiredArg()
                .ofType(ClientUpdateCheckMode.class)
                .defaultsTo(ClientUpdateCheckMode.AUTO)
                .withValuesConvertedBy(new EnumConverter<ClientUpdateCheckMode>(ClientUpdateCheckMode.class)
                {
                    @Override
                    public ClientUpdateCheckMode convert(String v)
                    {
                        return super.convert(v.toUpperCase());
                    }
                });

        final ArgumentAcceptingOptionSpec<String> proxyInfo = parser
                .accepts("proxy")
                .withRequiredArg().ofType(String.class);



        parser.accepts("help", "Show this text").forHelp();
        OptionSet options = parser.parse(args);
        final boolean developerMode = options.has("developer-mode");

        if (options.has("help")) {
            parser.printHelpOn(System.out);
            System.exit(0);
        }

        if (options.has("proxy"))
        {
            String[] proxy = options.valueOf(proxyInfo).split(":");

            if (proxy.length >= 2)
            {
                System.setProperty("socksProxyHost", proxy[0]);
                System.setProperty("socksProxyPort", proxy[1]);
            }

            if (proxy.length >= 4)
            {
                System.setProperty("java.net.socks.username", proxy[2]);
                System.setProperty("java.net.socks.password", proxy[3]);

                final String user = proxy[2];
                final char[] pass = proxy[3].toCharArray();

                Authenticator.setDefault(new Authenticator()
                {
                    private PasswordAuthentication auth = new PasswordAuthentication(user, pass);

                    protected PasswordAuthentication getPasswordAuthentication()
                    {
                        return auth;
                    }
                });
            }
        }


        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
        {
            log.error("Uncaught exception:", throwable);
            if (throwable instanceof AbstractMethodError)
            {
                log.error("Classes are out of date; Build with maven again.");
            }
        });


        OkHttpClient.Builder okHttpClientBuilder = RuneLiteAPI.CLIENT.newBuilder()
                .cache(new Cache(new File(CACHE_DIR, "okhttp"), MAX_OKHTTP_CACHE_SIZE));

        final boolean insecureSkipTlsVerification = options.has("insecure-skip-tls-verification");
        if (insecureSkipTlsVerification || RuneLiteProperties.isInsecureSkipTlsVerification())
        {
            setupInsecureTrustManager(okHttpClientBuilder);
        }

        final OkHttpClient okHttpClient = okHttpClientBuilder.build();

        SplashScreen.init();
        SplashScreen.stage(0, "Retrieving client", "");

        try
        {
            final ClientLoader clientLoader = new ClientLoader(okHttpClient, options.valueOf(updateMode));


            new Thread(() ->
            {
                clientLoader.get();
                ClassPreloader.preload();
            }, "Preloader").start();


            PROFILES_DIR.mkdirs();

            final long start = System.currentTimeMillis();

            injector = Guice.createInjector(new BotModule(
                    okHttpClient,
                    clientLoader,
                    developerMode,
                    false,
                    options.valueOf(sessionfile),
                    options.valueOf(configfile)));

            setInjector(injector);

        if (options.has("bot-runelite")) {
            //setInjector();
            injector.getInstance(RuneLite.class).init();
        }
        else {
            injector.getInstance(RuneLite.class).init();
        }

            final long end = System.currentTimeMillis();
            final RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
            final long uptime = rb.getUptime();
            log.info("Client initialization took {}ms. Uptime: {}ms", end - start, uptime);
        }
        catch (Exception e)
        {
            log.warn("Failure during startup", e);
            SwingUtilities.invokeLater(() ->
                    new FatalErrorDialog("RuneLite has encountered an unexpected error during startup.")
                            .open());
        }
    }

    /**
     * Returns the size of the panel that clients should be drawn into. For
     * internal use.
     *
     * @return The client panel size.
     */
    public Dimension getPanelSize() {
        for (RuneLite bot : Application.getBots()) {
            if (bot != null) {
                if (bot.getClient().getClass().getClassLoader() == this.getClient().getClass().getClassLoader()) {
                    return bot.getPanel().getSize();
                }
            }
        }
        return null;
    }



    public void init() throws Exception {
        im = new InputManager(this);
        Executors.newSingleThreadScheduledExecutor().submit(() -> {
            while(this.getClient() == null){}
            if (getPanelSize() != null) {
                final Dimension size = getPanelSize();
                backBuffer = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
                image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
            }
        });
        psh = new PassiveScriptHandler(this);
        eventManager = new EventManager();
        sh = new ScriptHandler(this);
        bh = new BreakHandler(this);
        paintEvent = new PaintEvent();
        textPaintEvent = new TextPaintEvent();
        listeners = new TreeMap<>();
        pluginManager.add(new Botplugin(injector));
        setMethodContext();

        eventManager.start();

        this.start();
    }

    public void setMethodContext() {
        methods = new MethodContext(this);
        methods.bank.assignConstants();
    }

    public void shutdown() {
        getLoader().stop();
        getLoader().setVisible(false);
        eventManager.killThread(false);
        sh.stopScript();
        psh.stopScript();
        kill_passive = true;
    }

    public static void setInjector() {
        setInjector(RuneLite.injector);
    }


    private static class ConfigFileConverter implements ValueConverter<File>
    {
        @Override
        public File convert(String fileName)
        {
            final File file;

            if (Paths.get(fileName).isAbsolute()
                    || fileName.startsWith("./")
                    || fileName.startsWith(".\\"))
            {
                file = new File(fileName);
            }
            else
            {
                file = new File(net.runelite.client.RuneLite.RUNELITE_DIR, fileName);
            }

            if (file.exists() && (!file.isFile() || !file.canWrite()))
            {
                throw new ValueConversionException(String.format("File %s is not accessible", file.getAbsolutePath()));
            }

            return file;
        }

        @Override
        public Class<? extends File> valueType()
        {
            return File.class;
        }

        @Override
        public String valuePattern()
        {
            return null;
        }
    }

    private static void setupInsecureTrustManager(OkHttpClient.Builder okHttpClientBuilder)
    {
        try
        {
            X509TrustManager trustManager = new X509TrustManager()
            {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType)
                {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType)
                {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers()
                {
                    return new X509Certificate[0];
                }
            };

            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, new TrustManager[]{trustManager}, new SecureRandom());
            okHttpClientBuilder.sslSocketFactory(sc.getSocketFactory(), trustManager);
        }
        catch (NoSuchAlgorithmException | KeyManagementException ex)
        {
            log.warn("unable to setup insecure trust manager", ex);
        }
    }
}
