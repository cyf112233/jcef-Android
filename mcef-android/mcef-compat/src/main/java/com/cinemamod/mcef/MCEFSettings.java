package com.cinemamod.mcef;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/** 和桌面版同名，字段含义保留；Android 上真正起作用的是 hostPort 与 dataDirectory。 */
public class MCEFSettings {

    private int hostPort = 39090;
    private File dataDirectory;
    private File librariesDirectory;
    private boolean enableGPU = true;
    private String cefArgs = "";

    public MCEFSettings() {
        String dir = System.getProperty("mcef.data.path");
        if (dir != null) dataDirectory = new File(dir);
    }

    public static MCEFSettings load() {
        MCEFSettings s = new MCEFSettings();
        File f = new File(System.getProperty("user.dir", "."), "config/mcef-android.properties");
        if (!f.isFile()) return s;
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(f)) {
            p.load(in);
        } catch (IOException e) {
            return s;
        }
        s.hostPort = Integer.parseInt(p.getProperty("hostPort", "39090"));
        s.enableGPU = Boolean.parseBoolean(p.getProperty("enableGPU", "true"));
        s.cefArgs = p.getProperty("cefArgs", "");
        return s;
    }

    public int getHostPort() { return hostPort; }
    public void setHostPort(int port) { hostPort = port; }

    public File getDataDirectory() { return dataDirectory; }
    public void setDataDirectory(File dir) { dataDirectory = dir; }

    public File getLibrariesDirectory() { return librariesDirectory; }
    public void setLibrariesDirectory(File dir) { librariesDirectory = dir; }

    public boolean isEnableGPU() { return enableGPU; }
    public String getCefArgs() { return cefArgs; }
}
