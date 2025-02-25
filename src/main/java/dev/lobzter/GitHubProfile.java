package dev.lobzter;


import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class GitHubProfile {
    private static final String API_LEAGUE_KEY = System.getenv("API_LEAGUE_KEY");
    private static final String GITHUB_TOKEN = System.getenv("GH_API_TOKEN");

    static class ProfileSection {
        String name;
        List<ProfileItem> items;

        ProfileSection(String name, List<ProfileItem> items) {
            this.name = name;
            this.items = items;
        }
    }

    static class ProfileItem {
        String label;
        String value;

        ProfileItem(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }

    static class UserData {
        String username;
        String profilePicture;
        List<ProfileSection> sections;

        UserData(String username, String profilePicture, List<ProfileSection> sections) {
            this.username = username;
            this.profilePicture = profilePicture;
            this.sections = sections;
        }
    }

    private static String timeAgo(String dateString) {
        try {
            Instant past = Instant.parse(dateString);
            Instant now = Instant.now();
            long diffInSeconds = ChronoUnit.SECONDS.between(past, now);

            List<Object[]> units = List.of(
                    new Object[]{31536000L, "year"},
                    new Object[]{2592000L, "month"},
                    new Object[]{86400L, "day"},
                    new Object[]{3600L, "hour"},
                    new Object[]{60L, "minute"},
                    new Object[]{1L, "second"}
            );


            List<String> timeAgoParts = new ArrayList<>();
            for (Object[] unit : units) {
                long seconds = (long) unit[0];
                String label = (String) unit[1];
                long interval = diffInSeconds / seconds;
                if (interval >= 1) {
                    timeAgoParts.add(interval + " " + (interval > 1 ? label + "s" : label));
                    diffInSeconds -= interval * seconds;
                }
            }


            return timeAgoParts.isEmpty() ? "just now" :
                    String.join(", ", timeAgoParts.subList(0, Math.min(3, timeAgoParts.size()))) + " ago";
        } catch (DateTimeParseException e) {
            // Handle the case where the dateString is not in the expected format
            return "Error parsing date";
        }
    }

    private static String getAsciiArtFromImage(String imgUrl) throws IOException, InterruptedException {
        String apiUrl = String.format(
                "https://api.apileague.com/convert-image-to-ascii-txt?width=125&height=125&api-key=%s&url=%s",
                API_LEAGUE_KEY,
                imgUrl
        );

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("API request failed with status: " + response.statusCode());
        }

        return response.body();
    }

    private static UserData getUserData() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/user"))
                .header("Authorization", "Bearer " + GITHUB_TOKEN)
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("GitHub API request failed with status: " + response.statusCode());
        }

        JSONObject data = new JSONObject(response.body());

        List<ProfileSection> sections = new ArrayList<>();

        // First section
        sections.add(new ProfileSection(null, List.of(
                new ProfileItem("OS", "Arch Linux"),
                new ProfileItem("IDE", "Code OSS,IntelliJ"),
                new ProfileItem("Languages", "Java, Python,TypeScript"),
                new ProfileItem("Frameworks", "Express, Spring boot, Android Native")
        )));

        // Profile section
        sections.add(new ProfileSection("Profile", List.of(
                new ProfileItem("Role", "Software Developer"),
                new ProfileItem("Status", "I Understand it now"),
                new ProfileItem("Twitter", "@lobz03")
//                new ProfileItem("Portfolio", "#")
        )));

        // GitHub section
        sections.add(new ProfileSection("GitHub", List.of(
                new ProfileItem("Audience",
                        data.optInt("followers", 0) + " Followers | " +
                                data.optInt("following", 0) + " Following"
                ),
                new ProfileItem("Repos",
                        (data.optInt("public_repos") + data.optInt("total_private_repos")) +
                                " (" + data.optInt("public_repos") + " Public | " +
                                data.optInt("total_private_repos") + " Private)"
                ),
                new ProfileItem("Joined", timeAgo(data.getString("created_at"))),
//                new ProfileItem("Contributions", "2087 (Aug 10, 2021 - Present)"),
//                new ProfileItem("Current Streak", "0 (Nov 7)"),
                new ProfileItem("Longest Streak", "24 (Mar 9th, 2024 - Mar 30, 2023)")
        )));

        return new UserData(
                data.optString("login", "Tomiloba21"),
                data.getString("avatar_url"),
                sections
        );
    }

    private static String generateSVGArt(String art) {
        String[] chunks = art.split("\n");
        StringBuilder svgArt = new StringBuilder();
        double y = 100;
        int x = 100;
        double yInc = 8.6;

        for (String chunk : chunks) {
            if (!chunk.isEmpty()) {
                svgArt.append(String.format("<tspan x=\"%d\" y=\"%.2f\">%s</tspan>\n", x, y, chunk));
                y += yInc;
            }
        }

        return String.format("<text x=\"100\" y=\"100\" fill=\"#c9d1d9\" class=\"ascii\">\n%s</text>", svgArt);
    }

    private static String generateSVGText(UserData data) {
        double yPosition = 150;
        double lineHeight = (1282 - yPosition * 2) / 22;
        List<String> svgLines = new ArrayList<>();

        // Username
        svgLines.add(String.format("<tspan x=\"1300\" y=\"%.2f\">%s@GitHub</tspan>", yPosition, data.username));
        yPosition += lineHeight;
        svgLines.add(String.format("<tspan x=\"1300\" y=\"%.2f\">---------------</tspan>", yPosition));
        yPosition += lineHeight;

        // Sections
        for (ProfileSection section : data.sections) {
            // Section title
            if (section.name != null) {
                svgLines.add(String.format("<tspan x=\"1300\" y=\"%.2f\" class=\"keyColor\">%s</tspan>:", yPosition, section.name));
                yPosition += lineHeight;
                svgLines.add(String.format("<tspan x=\"1300\" y=\"%.2f\">——————</tspan>", yPosition));
                yPosition += lineHeight;
            }

            // Section items
            for (ProfileItem item : section.items) {
                svgLines.add(String.format(
                        "<tspan x=\"1300\" y=\"%.2f\" class=\"keyColor\">%s</tspan>: <tspan class=\"valueColor\">%s</tspan>",
                        yPosition, item.label, item.value
                ));
                yPosition += lineHeight;
            }

            // Extra space between sections
            yPosition += lineHeight;
        }

        return String.format(
                "<text x=\"500\" y=\"150\" fill=\"#c9d1d9\" font-size=\"40px\">\n%s\n</text>",
                String.join("\n", svgLines)
        );
    }

    private static void writeSVG(String svgContent) throws IOException {
        File outputDir = new File("output");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        Files.writeString(Paths.get("output/neofetch.svg"), svgContent);
    }

    private static void writeIndexHtml() throws IOException {
        String html = "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>GitHub Profile</title>\n" +
                "    <style>\n" +
                "        body {\n" +
                "            margin: 0;\n" +
                "            padding: 0;\n" +
                "            background-color: #0d1117;\n" +
                "            color: #c9d1d9;\n" +
                "            font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Helvetica, Arial, sans-serif;\n" +
                "            display: flex;\n" +
                "            justify-content: center;\n" +
                "            align-items: center;\n" +
                "            min-height: 100vh;\n" +
                "        }\n" +
                "        \n" +
                "        .container {\n" +
                "            max-width: 95%;\n" +
                "            padding: 20px;\n" +
                "        }\n" +
                "        \n" +
                "        .svg-wrapper {\n" +
                "            width= 100% ;\n"+
                "            overflow: auto;\n" +
                "            border-radius: 15px;\n" +
                "            box-shadow: 0 8px 24px rgba(0,0,0,0.2);\n" +
                "        }\n" +
                "        \n" +
                "        svg {\n" +
                "            display: block;\n" +
                "            max-width: 100%;\n" +
                "            height: auto;\n" +
                "        }\n" +
                "        \n" +
                "        h1 {\n" +
                "            text-align: center;\n" +
                "            margin-bottom: 1.5rem;\n" +
                "            font-weight: 600;\n" +
                "            color: #58a6ff;\n" +
                "        }\n" +
                "        \n" +
                "        footer {\n" +
                "            text-align: center;\n" +
                "            margin-top: 2rem;\n" +
                "            font-size: 0.9rem;\n" +
                "            color: #8b949e;\n" +
                "        }\n" +
                "        \n" +
                "        a {\n" +
                "            color: #58a6ff;\n" +
                "            text-decoration: none;\n" +
                "        }\n" +
                "        \n" +
                "        a:hover {\n" +
                "            text-decoration: underline;\n" +
                "        }\n" +
                "        \n" +
                "        @media (max-width: 768px) {\n" +
                "            .container {\n" +
                "                padding: 10px;\n" +
                "            }\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <h1>GitHub Profile</h1>\n" +
                "        <div class=\"svg-wrapper\">\n" +
                "            <object data=\"./neofetch.svg\" type=\"image/svg+xml\" width=\"100%\"></object>\n" +
                "        </div>\n" +
                "        <footer>\n" +
                "            Generated with <a href=\"https://github.com/Tomiloba21/Tomiloba21\">GitHub Profile Generator</a>\n" +
                "        </footer>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";

        File outputDir = new File("output");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        Files.writeString(Paths.get("output/index.html"), html);
    }

//    public static void main(String[] args) {
//        try {
//            UserData user = getUserData();
//            System.out.println("Gotten user data");
//
//            String art = getAsciiArtFromImage(user.profilePicture);
//            System.out.println("Gotten ASCII text from image");
//
//            String svgArt = generateSVGArt(art);
//            System.out.println("Generated SVG Art section");
//
//            String svgText = generateSVGText(user);
//            System.out.println("Generated SVG Text section");
//
//            String svg = String.format(
//                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
//                            "<svg xmlns=\"http://www.w3.org/2000/svg\" font-family=\"Andale Mono,AndaleMono,Consolas,monospace\" width=\"2500px\" height=\"1282px\" font-size=\"16px\">\n" +
//                            "<style>\n" +
//                            "    .keyColor {fill: #ffa657;}\n" +
//                            "    .valueColor {fill: #a5d6ff;}\n" +
//                            "    .addColor {fill: #3fb950;}\n" +
//                            "    .delColor {fill: #f85149;}\n" +
//                            "    .commentColor {fill: #8b949e;}\n" +
//                            "    text, tspan {white-space: pre;}\n" +
//                            "</style>\n" +
//                            "\n" +
//                            "<rect width=\"2500px\" height=\"1282px\" fill=\"#161b22\" rx=\"15\" />\n" +
//                            "\n%s\n%s\n" +
//                            "</svg>",
//                    svgArt, svgText
//            );
//
//            writeSVG(svg);
//            System.out.println("Saved SVG to output/neofetch.svg");
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }


    public static void main(String[] args) {
        try {
            UserData user = getUserData();
            System.out.println("Gotten user data");

            String art = getAsciiArtFromImage(user.profilePicture);
            System.out.println("Gotten ASCII text from image");

            String svgArt = generateSVGArt(art);
            System.out.println("Generated SVG Art section");

            String svgText = generateSVGText(user);
            System.out.println("Generated SVG Text section");

            String svg = String.format(
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                            "<svg xmlns=\"http://www.w3.org/2000/svg\" font-family=\"Andale Mono,AndaleMono,Consolas,monospace\" width=\"2500px\" height=\"1282px\" font-size=\"16px\">\n" +
                            "<style>\n" +
                            "    .keyColor {fill: #ffa657;}\n" +
                            "    .valueColor {fill: #a5d6ff;}\n" +
                            "    .addColor {fill: #3fb950;}\n" +
                            "    .delColor {fill: #f85149;}\n" +
                            "    .commentColor {fill: #8b949e;}\n" +
                            "    text, tspan {white-space: pre;}\n" +
                            "</style>\n" +
                            "\n" +
                            "<rect width=\"2500px\" height=\"1282px\" fill=\"#161b22\" rx=\"15\" />\n" +
                            "\n%s\n%s\n" +
                            "</svg>",
                    svgArt, svgText
            );

            writeSVG(svg);
            System.out.println("Saved SVG to output/neofetch.svg");

            // Write the index.html file
            writeIndexHtml();
            System.out.println("Saved index.html to output/index.html");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}