package com.example.demo;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class RemoteImageDefaultsTests {
    private static final Path MAIN = Path.of("src/main");
    private static final String MENU = "/images/placeholders/menu-item.svg";
    private static final String AVATAR = "/images/placeholders/avatar.svg";

    @Test void dataInitializerUsesLocalMenuPlaceholder() throws IOException { String s = read("java/com/example/demo/config/DataInitializer.java"); assertTrue(s.contains(MENU)); assertFalse(s.contains("images.unsplash.com")); }
    @Test void customerIndexUsesLocalMenuFallback() throws IOException { assertMenu("resources/templates/customer/index.html"); }
    @Test void customerMenuUsesLocalMenuFallback() throws IOException { assertMenu("resources/templates/customer/menu.html"); }
    @Test void customerSearchUsesLocalMenuFallback() throws IOException { assertMenu("resources/templates/customer/search.html"); }
    @Test void customerProfileUsesLocalMenuFallback() throws IOException { assertMenu("resources/templates/customer/profile.html"); }
    @Test void adminTableDetailUsesLocalMenuFallback() throws IOException { assertMenu("resources/templates/admin/table-detail.html"); }
    @Test void aboutPageUsesLocalAssets() throws IOException { String s=read("resources/templates/customer/about.html"); assertTrue(s.contains(AVATAR)); assertTrue(s.contains("/images/about/about-story.svg")); assertFalse(s.matches("(?s).*<img[^>]+src=\"https?://.*")); }
    @Test void styleCssHasNoRemoteBackgroundUrl() throws IOException { String s=read("resources/static/css/style.css").toLowerCase(); assertFalse(Pattern.compile("(?i)(?:background|background-image)\\s*:[^;}]*url\\(\\s*['\"]?https?://").matcher(s).find()); assertFalse(s.contains("images.unsplash.com")); }
    @Test void placeholdersExist() { assertTrue(Files.exists(MAIN.resolve("resources/static/images/placeholders/menu-item.svg"))); assertTrue(Files.exists(MAIN.resolve("resources/static/images/placeholders/avatar.svg"))); }
    @Test void aboutAssetsExistWithoutRemoteReferences() throws IOException { for (String name : new String[]{"about-story.svg", "about-community.svg"}) { Path p=MAIN.resolve("resources/static/images/about/"+name); assertTrue(Files.exists(p)); assertFalse(Pattern.compile("(?i)(?:href|xlink:href|src)\\s*=\\s*['\"]https?://|url\\(\\s*['\"]?https?://").matcher(Files.readString(p)).find()); } }
    private void assertMenu(String relative) throws IOException { String s=read(relative); assertTrue(s.contains(MENU)); assertFalse(s.contains("images.unsplash.com")); }
    private String read(String relative) throws IOException { return Files.readString(MAIN.resolve(relative)); }
}
