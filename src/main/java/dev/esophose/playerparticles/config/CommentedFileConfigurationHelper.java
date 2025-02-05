package dev.esophose.playerparticles.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommentedFileConfigurationHelper {

    /**
     * Get new configuration
     *
     * @param file - Path to file
     * @return - New SimpleConfig
     */
    public CommentedFileConfiguration getNewConfig(File file) {
        if (file.isDirectory())
            throw new IllegalArgumentException("Cannot create configuration from directory");

        File parent = file.getParentFile();
        if (!parent.exists())
            parent.mkdirs();

        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return new CommentedFileConfiguration(this.getConfigContent(file), file, this.getCommentsNum(file));
    }

    /**
     * Read file and make comments SnakeYAML friendly
     *
     * @param file - Path to file
     * @return - File as Input Stream
     */
    public Reader getConfigContent(File file) {
        if (!file.exists())
            return new InputStreamReader(new ByteArrayInputStream(new byte[0]));

        try {
            int commentNum = 0;

            StringBuilder whole = new StringBuilder();
            BufferedReader reader = Files.newBufferedReader(Paths.get(file.getAbsolutePath()), StandardCharsets.UTF_8);

            String currentLine;
            while ((currentLine = reader.readLine()) != null) {
                // Convert comments into keys
                if (currentLine.trim().startsWith("#")) {
                    String addLine = currentLine.replaceAll(Pattern.quote("'"), Matcher.quoteReplacement("''"))
                            .replaceFirst("#", "_COMMENT_" + commentNum++ + ": '") + "'";
                    whole.append(addLine).append("\n");
                } else {
                    whole.append(currentLine).append("\n");
                }
            }

            String config = whole.toString();
            Reader configStream = new StringReader(config);

            reader.close();
            return configStream;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Get comments from file
     *
     * @param file - File
     * @return - Comments number
     */
    private int getCommentsNum(File file) {
        if (!file.exists())
            return 0;

        try {
            int comments = 0;
            String currentLine;

            BufferedReader reader = Files.newBufferedReader(Paths.get(file.getAbsolutePath()), StandardCharsets.UTF_8);

            while ((currentLine = reader.readLine()) != null)
                if (currentLine.trim().startsWith("#"))
                    comments++;

            reader.close();
            return comments;
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
    }

    private String prepareConfigString(String configString) {
        boolean lastLine = false;

        String[] lines = configString.split("\n");
        StringBuilder config = new StringBuilder();

        for (String line : lines) {
            if (line.trim().startsWith("_COMMENT")) {
                int whitespaceIndex = line.indexOf(line.trim());
                String comment = line.substring(0, whitespaceIndex) + "#" + line.substring(line.indexOf(":") + 3, line.length() - 1);

                String normalComment;
                if (comment.trim().startsWith("#'")) {
                    normalComment = comment.substring(0, comment.length() - 1).replaceFirst("#'", "# ");
                } else {
                    normalComment = comment;
                }

                normalComment = normalComment.replaceAll("''", "'");

                if (!lastLine) {
                    config.append(normalComment).append("\n");
                } else {
                    config.append("\n").append(normalComment).append("\n");
                }

                lastLine = false;
            } else {
                config.append(line).append("\n");
                lastLine = true;
            }
        }

        return config.toString();
    }

    /**
     * Saves configuration to file
     *
     * @param configString - Config string
     * @param file - Config file
     * @param compactLines - If lines should forcefully be separated by only one newline character
     */
    public void saveConfig(String configString, File file, boolean compactLines) {
        String configuration = this.prepareConfigString(configString).replaceAll("\n\n", "\n");

        // Apply post-processing to config string to make it pretty
        StringBuilder stringBuilder = new StringBuilder();
        try (Scanner scanner = new Scanner(configuration)) {
            boolean lastLineHadContent = false;
            int lastCommentSpacing = -1;
            int lastLineSpacing = -1;
            boolean forceCompact = false;

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();

                boolean lineHadContent = false;
                boolean lineWasComment = false;
                int commentSpacing = -1;
                int lineSpacing = line.indexOf(line.trim());

                if (line.trim().startsWith("#")) {
                    lineWasComment = true;
                    String trimmed = line.trim().replaceFirst("#", "");
                    commentSpacing = trimmed.indexOf(trimmed.trim());
                } else if (!line.trim().isEmpty()) {
                    lineHadContent = true;
                    if (line.trim().startsWith("-"))
                        forceCompact = true;
                }

                if (!compactLines && !forceCompact && (
                        (lastLineSpacing != -1 && lineSpacing != lastLineSpacing)
                                || (commentSpacing != -1 && commentSpacing <= 3 && lastCommentSpacing > 3)
                                || (lastLineHadContent && lineHadContent)
                                || (lineWasComment && lastLineHadContent))
                        && !(lastLineHadContent && !lineWasComment)) {
                    stringBuilder.append('\n');
                }

                stringBuilder.append(line).append('\n');

                lastLineHadContent = lineHadContent;
                lastCommentSpacing = commentSpacing;
                lastLineSpacing = lineSpacing;
                forceCompact = false;
            }
        }

        // Remove all spaces from "empty" lines and replace with a single newline
        // Only allow at maximum two newlines in a row
        StringBuilder compactedBuilder = new StringBuilder();
        try (Scanner scanner = new Scanner(stringBuilder.toString())) {
            int consecutiveNewlines = 0;
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.trim().isEmpty()) {
                    consecutiveNewlines++;
                    if (consecutiveNewlines < 2)
                        compactedBuilder.append('\n');
                } else {
                    consecutiveNewlines = 0;
                    compactedBuilder.append(line).append('\n');
                }
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(file.getAbsolutePath()), StandardCharsets.UTF_8)) {
            writer.write(compactedBuilder.toString());
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
