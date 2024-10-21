package com.github.mkouba.wsperf;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.quarkus.runtime.annotations.QuarkusMain;
import io.vertx.core.json.JsonObject;

@QuarkusMain(name = "summary")
public class SummaryTable {

    public static void main(String[] args) {

        if (args.length == 0) {
            return;
        }

        List<File> files = new ArrayList<File>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            File file = new File(arg);
            if (!file.canRead()) {
                throw new IllegalArgumentException("Unable to read the data file: " + file);
            }
            if (file.isFile()) {
                files.add(file);
            } else if (file.isDirectory()) {
                Collections.addAll(files, file.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        return pathname.isFile() && !pathname.isHidden() && pathname.getName().endsWith(".json");
                    }
                }));
            }
        }

        if (files.isEmpty()) {
            return;
        }
        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                String f1Name = f1.getName();
                String f2Name = f2.getName();
                if (f1Name.startsWith("999-SNAPSHOT")) {
                    return 1;
                }
                if (f2Name.startsWith("999-SNAPSHOT")) {
                    return -1;
                }
                String[] f1Parts = f1Name.split("\\.");
                String[] f2Parts = f2Name.split("\\.");

                int result = 0;
                for (int i = 0; i < 3; i++) {
                    int f1Val = Integer.parseInt(f1Parts[i]);
                    int f2Val = Integer.parseInt(f2Parts[i]);
                    result = Integer.compare(f1Val, f2Val);
                    if (result != 0) {
                        return result;
                    }
                }
                return result;
            }
        });

        // Print the summary table
        final String sep = "|";
        System.out.println(
                padLeft("Version") + sep + padLeft("Clients") + sep + padLeft("Messages") + sep + padLeft("Time") + sep);
        for (File file : files) {
            try {
                JsonObject json = new JsonObject(Files.readString(file.toPath(), StandardCharsets.UTF_8));
                System.out.println(padRight(json.getString("quarkusVersion")) + sep
                        + padLeft(json.getString("numberOfClients")) + sep
                        + padLeft(json.getString("numberOfMessages")) + sep
                        + padLeft(json.getString("timeTaken") + "ms") + sep);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to read result file: " + file);
            }
        }
    }

    static int DEFAULT_PAD = 15;

    static String padRight(String val, int pad) {
        return String.format("%1$-" + pad + "s", val);
    }

    static String padLeft(String val, int pad) {
        return String.format("%1$" + pad + "s", val);
    }

    static String padRight(String val) {
        return padRight(val, DEFAULT_PAD);
    }

    static String padLeft(String val) {
        return padLeft(val, DEFAULT_PAD);
    }

}