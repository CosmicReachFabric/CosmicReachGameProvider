package net.cosmicreachfabric.cosmicreach.provider.services;

import net.cosmicreachfabric.cosmicreach.provider.patch.CosmicReachEntrypointPatch;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.impl.FormattedException;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.game.GameProviderHelper;
import net.fabricmc.loader.impl.game.patch.GameTransformer;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.metadata.BuiltinModMetadata;
import net.fabricmc.loader.impl.metadata.ContactInformationImpl;
import net.fabricmc.loader.impl.util.Arguments;
import net.fabricmc.loader.impl.util.SystemProperties;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.fabricmc.loader.impl.util.log.LogLevel;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixins;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class CosmicReachGameProvider implements GameProvider {

    private static final String ENTRYPOINT = "finalforeach.cosmicreach.lwjgl3.Lwjgl3Launcher";
    private static final Set<String> SENSITIVE_ARGS = new HashSet<>(Arrays.asList(
            "savedir",
            "debug",
            "localclient"));

    private Arguments arguments;
    private String entrypoint;
    private Path launchDir;
    private Path libDir;
    private Path gameJar;
    private boolean development = false;
    private final List<Path> miscGameLibraries = new ArrayList<>();
    private static Version gameVersion;

    private static final GameTransformer TRANSFORMER = new GameTransformer(
            new CosmicReachEntrypointPatch());

    @Override
    public String getGameId() {
        return "cosmicreach";
    }

    @Override
    public String getGameName() {
        return "Cosmic Reach";
    }

    @Override
    public String getRawGameVersion() {
        return getGameVersion().getFriendlyString();
    }

    @Override
    public String getNormalizedGameVersion() {
        return getRawGameVersion();
    }

    @Override
    public Collection<BuiltinMod> getBuiltinMods() {

        HashMap<String, String> cosmicReachContactInformation = new HashMap<>();
        cosmicReachContactInformation.put("homepage", "https://finalforeach.itch.io/cosmic-reach");
        cosmicReachContactInformation.put("wiki", "https://cosmicreach.wiki/"); //Maintained by @fesiug
        cosmicReachContactInformation.put("discord", "https://discord.com/invite/R9JEMVzA");
        cosmicReachContactInformation.put("issues", "https://github.com/FinalForEach/Cosmic-Reach-Issue-Tracker");

        BuiltinModMetadata.Builder cosmicReachMetaData =
                new BuiltinModMetadata.Builder(getGameId(), getNormalizedGameVersion())
                        .setName(getGameName())
                        .addAuthor("FinalForEach", cosmicReachContactInformation)
                        .setContact(new ContactInformationImpl(cosmicReachContactInformation))
                        .setDescription("A futuristic themed block game made as part of a youtube devlog series.");

        return Collections.singletonList(new BuiltinMod(Collections.singletonList(gameJar), cosmicReachMetaData.build()));
    }

    @Override
    public String getEntrypoint() {
        return entrypoint;
    }

    @Override
    public Path getLaunchDirectory() {
        if (arguments == null)
            return Paths.get(".");

        return getLaunchDirectory(arguments);
    }

    @Override
    public boolean isObfuscated() {
        return false;
    }

    @Override
    public boolean requiresUrlClassLoader() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean locateGame(FabricLauncher launcher, String[] args) {
        this.arguments = new Arguments();
        arguments.parse(args);
        String version;
        String backupVersion = "";

        Map<Path, ZipFile> zipFiles = new HashMap<>();

        if(Objects.equals(System.getProperty(SystemProperties.DEVELOPMENT), "true")) {
            development = true;
        }

        try {
            String gameJarProperty = System.getProperty(SystemProperties.GAME_JAR_PATH);
            String entrypoint = "";
            GameProviderHelper.FindResult result = null;
            if(gameJarProperty == null) {
                Path xml = Paths.get("./app/.jpackage.xml");
                Path config = Paths.get("./app/Cosmic Reach.cfg");
                if (Files.exists(xml)) {
                    SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
                    GameJarCaptureXML handler = new GameJarCaptureXML();
                    parser.parse(xml.toFile(), handler);
                    gameJarProperty = handler.getGameJarProperty();
                    entrypoint = handler.getMainClass();
                    backupVersion = handler.getVersion();
                    System.out.println("Loading GameJar data from .jpackage.xml");
                } else {
                    if (Files.exists(config)) {
                        BufferedReader reader = new BufferedReader(new FileReader(config.toFile()));
                        for (String line : reader.lines().toList()) {
                            if (line.toLowerCase().contains("app.classpath"))
                                gameJarProperty = "./app/" + line.replace("app.classpath=$APPDIR\\", "");
                            if (line.toLowerCase().contains("app.mainclass"))
                                entrypoint = line.replace("app.mainclass=", "");
                            if (line.toLowerCase().contains("java-options"))
                                backupVersion = line.replace("java-options=-Djpackage.app-version=", "");
                        }
                        System.out.println("Loading GameJar data from Cosmic Reach.cfg");
                    } else {
                        //TODO Temp until more time to implement failsafe for non-itch location
                        gameJarProperty = "./app/Cosmic Reach-0.1.0.jar";
                        System.out.println("Loading GameJar data from Failsafe location");
                    }
                }
            }
            if(gameJarProperty != null) {
                Path path = Paths.get(gameJarProperty);
                if (!Files.exists(path)) {
                    throw new RuntimeException("Game jar configured through " + SystemProperties.GAME_JAR_PATH + " system property doesn't exist");
                }
                if (entrypoint.isEmpty()) {
                    entrypoint = ENTRYPOINT;
                }

                result = GameProviderHelper.findFirst(Collections.singletonList(path), zipFiles, true, entrypoint);
            }

            if(result == null) {
                return false;
            }

            this.entrypoint = result.name;
            gameJar = result.path;

        } catch (Exception e) {
            e.printStackTrace();
        }

        processArgumentMap(arguments);

        version = readVersion();
        if (version.isEmpty())
            version = backupVersion;

        if (version != null) {
            try {
                setGameVersion(Version.parse(version));
            } catch (VersionParsingException e) {
                e.printStackTrace();
            }
        }

        return true;
    }

    @Nullable
    private String readVersion() {
        String version = null;

        try (ZipFile game = new ZipFile(gameJar.toFile())) {
            ZipEntry entry = game.getEntry("build_assets/version.txt");
            if (entry == null) {
                throw new RuntimeException("Version file not found in the JAR");
            }
            InputStream stream = game.getInputStream(entry);
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            version = reader.readLine();;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return version;
    }

    @Override
    public void initialize(FabricLauncher launcher) {
        TRANSFORMER.locateEntrypoints(launcher, Collections.singletonList(gameJar));
    }

    @Override
    public GameTransformer getEntrypointTransformer() {
        return TRANSFORMER;
    }

    @Override
    public void unlockClassPath(FabricLauncher launcher) {
        launcher.addToClassPath(gameJar);

        for(Path lib : miscGameLibraries) {
            launcher.addToClassPath(lib);
        }
    }

    @Override
    public void launch(ClassLoader loader) {
        Mixins.addConfiguration("cosmicreach.mixins.json");
        String targetClass = entrypoint;

        try {
            Class<?> c = loader.loadClass(targetClass);
            Method m = c.getMethod("main", String[].class);
            m.invoke(null, (Object) arguments.toArray());
        }
        catch(InvocationTargetException e) {
            throw new FormattedException("Cosmic Reach has crashed!", e.getCause());
        }
        catch(ReflectiveOperationException e) {
            throw new FormattedException("Failed to start Cosmic Reach", e);
        }
    }

    @Override
    public Arguments getArguments() {
        return arguments;
    }

    @Override
    public String[] getLaunchArguments(boolean sanitize) {
        if (arguments == null) return new String[0];

        String[] ret = arguments.toArray();
        if (!sanitize) return ret;

        int writeIdx = 0;

        for (int i = 0; i < ret.length; i++) {
            String arg = ret[i];

            if (i + 1 < ret.length
                    && arg.startsWith("--")
                    && SENSITIVE_ARGS.contains(arg.substring(2).toLowerCase(Locale.ENGLISH))) {
                if (arg.substring(2).equals("debug")) {
                    Log.shouldLog(LogLevel.DEBUG, LogCategory.GENERAL);
                }
                i++; // skip value
            } else {
                ret[writeIdx++] = arg;
            }
        }

        if (writeIdx < ret.length) ret = Arrays.copyOf(ret, writeIdx);

        return ret;
    }

    private void processArgumentMap(Arguments arguments) {
        if (!arguments.containsKey("gameDir")) {
            arguments.put("gameDir", getLaunchDirectory(arguments).toAbsolutePath().normalize().toString());
        }

        launchDir = Path.of(arguments.get("gameDir"));
        System.out.println("Launch directory is " + launchDir);
        libDir = launchDir.resolve(Path.of("./lib"));
    }

    private static Path getLaunchDirectory(Arguments arguments) {
        return Paths.get(arguments.getOrDefault("gameDir", "."));
    }

    public static void setGameVersion(Version version) {
        if (version != null) {
            gameVersion = version;
        }
    }

    private Version getGameVersion() {
        return gameVersion;
    }

    /**
     * Reads the {@code .jpackage.xml} file included in the itch.io release
     */
    private static class GameJarCaptureXML extends DefaultHandler {
        Boolean app_version = false;
        Boolean main_launcher = false;
        Boolean main_class = false;
        Boolean signed = false;
        Boolean app_store = false;

        String version = "";
        String name = "";
        String clazz = "";
        Boolean isSigned = false;
        Boolean isAppStore = false;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            app_version = qName.equalsIgnoreCase("app-version");
            main_launcher = qName.equalsIgnoreCase("main-launcher");
            main_class = qName.equalsIgnoreCase("main-class");
            signed = qName.equalsIgnoreCase("signed");
            app_store = qName.equalsIgnoreCase("app-store");
        }

        @Override
        public void characters(char ch[], int start, int length) throws SAXException {
            String contents = new String(ch, start, length);
            Boolean value = Boolean.getBoolean(contents);
            if (app_version) {
                version = contents;
                app_version = false;
            }
            if (main_launcher) {
                name = contents;
                main_launcher = false;
            }
            if (main_class) {
                clazz = contents;
                main_class = false;
            }
            if (signed) {
                isSigned = value;
                signed = false;
            }
            if (app_store) {
                isAppStore = value;
                app_version = false;
            }
        }

        @Nullable
        public String getGameJarProperty() {
            return !name.isEmpty() && !version.isEmpty() ?  "./app/" + name + "-" + version + ".jar" : null;
        }

        @Nullable
        public String getMainClass() {
            return !clazz.isEmpty() ? clazz : null;
        }

        @Nullable
        public String getVersion() {
            return !version.isEmpty() ? version : null;
        }
    }

}
