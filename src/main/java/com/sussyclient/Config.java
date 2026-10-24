package com.sussyclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    public static Config I = new Config();

    // Sus Chunk Finder
    public boolean enabled = false;
    public int sensitivity = 3;          // min fully grown clusters in a chunk to flag it
    public int simDistance = 12;         // chunks around you to scan
    public int maxY = 0;                 // only count clusters below this Y
    public int scanSpeed = 4;            // chunks scanned per tick
    public int boxHeight = 64;           // blocks tall, starting at world bottom
    public int chunkSize = 100;          // % of the chunk footprint
    public float outlineThickness = 0.15f;
    public int fillOpacity = 25;         // %
    public int red = 255, green = 60, blue = 60;
    public boolean outline = true;
    public boolean fill = true;
    public boolean throughWalls = true;
    public boolean chatNotify = true;


    // Activity Inspector
    public boolean aiEnabled = false;
    public int aiSensitivity = 2;        // min "unnatural" blocks around a spawner to flag the chunk
    public int aiRadius = 12;            // chunks to scan
    public int aiScanSpeed = 4;
    public int aiMaxY = 64;              // only spawners below this Y
    public int aiSlabY = 62;
    public float aiSlabThickness = 1.0f; // blocks
    public int aiSlabSize = 100;         // % of chunk
    public int aiOpacity = 40;           // %
    public int aiRed = 255, aiGreen = 200, aiBlue = 0;
    public boolean aiOutline = true;
    public boolean aiFill = true;
    public boolean aiThroughWalls = true;
    public boolean aiChat = true;
    public boolean aiCoords = true;
    public int rtpRegion = 0;            // 0 east,1 west,2 eu west,3 eu central,4 asia,5 oceania
    public int rtpDelay = 100;           // ticks between /rtp
    public boolean crownEnabled = false;
    public String crownUsername = "";    // edit in config/sussyclient.json
    public boolean crownFeedback = true;
    public int crownTimeout = 30;        // seconds
    public boolean rtpAfterTimeout = false;


    // Sus Patterns (port of Sussy Debug "Sus Chunk Finder")
    public boolean spEnabled = false;
    public int spSensitivity = 3;
    public int spRadius = 12;
    public int spScanSpeed = 4;
    public boolean spAmethyst = true;       // stripped budding amethyst (no buds/clusters around it)
    public boolean spJungleVines = false;
    public boolean spCaveVines = false;
    public boolean spKelp = false;
    public boolean spBerries = false;
    public boolean spSugarCane = false;
    public boolean spCocoa = false;
    public int spPlantMaxY = 0;             // plants only count below this Y
    public int spBoxHeight = 64;
    public int spBoxSize = 100;
    public float spThickness = 0.15f;
    public int spFillOpacity = 25;
    public int spRed = 170, spGreen = 60, spBlue = 255;
    public boolean spOutline = true, spFill = true, spThroughWalls = true, spChat = true;

    // Kr Sus Chunk (port of Sussy Debug "Kr Sus Chunk")
    public boolean krEnabled = false;
    public int krRadius = 12;
    public int krScanSpeed = 4;
    public int krThreshold = 6;
    public boolean krIgnoreTrial = true;
    public boolean krOnlyBelow = true;
    public int krMaxY = 0;
    public boolean krChat = true;
    public int krBoxHeight = 64;
    public int krBoxSize = 100;
    public float krThickness = 0.15f;
    public int krFillOpacity = 25;
    public int krLowRed = 255, krLowGreen = 200, krLowBlue = 0;
    public int krHighRed = 255, krHighGreen = 50, krHighBlue = 50;
    public boolean krOutline = true, krFill = true, krThroughWalls = true;


    // Tuff Chunk Finder (port of Sussy Debug "Tuff Chunk Finder")
    public boolean tcEnabled = false;
    public int tcSensitivity = 3;
    public int tcRadius = 12;
    public int tcScanSpeed = 4;
    public boolean tcPoweredRepeaters = true;
    public boolean tcAnyRepeaters = false;
    public boolean tcHives = true;
    public boolean tcCobbledDeepslate = true;
    public boolean tcVines = true;
    public boolean tcSeagrass = true;
    public boolean tcAmethyst = true;     // fully grown amethyst clusters below the natural Y limit
    public int tcMaxY = 320;              // repeaters/hives count below this Y
    public int tcNaturalMaxY = 0;         // cobbled deepslate, vines, seagrass count below this Y
    public int tcSlabY = 62;
    public float tcSlabThickness = 1.0f;
    public int tcSlabSize = 100;
    public float tcOutlineThickness = 0.15f;
    public int tcFillOpacity = 40;
    public int tcRed = 120, tcGreen = 200, tcBlue = 255;
    public boolean tcOutline = true, tcFill = true, tcThroughWalls = true, tcChat = true;


    // Sussy Chunk v3
    public boolean v3Enabled = false;
    public int v3SimDistance = 12;
    public int v3ScanSpeed = 4;
    public int v3MinFlags = 3;            // min flagged clusters to show a chunk
    public int v3MaxY = 0;                // only clusters below this Y
    public boolean v3Smart = true;        // smart adjustment: ignore clusters inside natural geodes
    public int v3ShellDistance = 4;       // how far to look for calcite / smooth basalt
    public boolean v3Encloser = false;    // only count clusters in small sealed pockets
    public int v3MaxPocket = 64;          // max air blocks in a "sealed" pocket
    public int v3Red = 255, v3Green = 40, v3Blue = 200;
    public int v3Alpha = 30;              // % fill opacity
    public int v3BoxHeight = 64;
    public int v3BoxSize = 100;
    public float v3Thickness = 0.15f;
    public boolean v3Outline = true, v3Fill = true, v3ThroughWalls = true, v3Chat = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("sussyclient.json"); }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                Config c = GSON.fromJson(Files.readString(p), Config.class);
                if (c != null) I = c;
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void save() {
        try { Files.writeString(path(), GSON.toJson(I)); } catch (Exception e) { e.printStackTrace(); }
    }
}
