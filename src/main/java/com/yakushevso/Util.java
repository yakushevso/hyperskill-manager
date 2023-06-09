package com.yakushevso;

import com.google.gson.*;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;
import java.lang.reflect.Type;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.yakushevso.data.Data;
import com.yakushevso.data.Step;
import com.yakushevso.data.Project;
import com.yakushevso.data.Topic;

public class Util {
    public static WebDriver driver;
    private static final String CHROMEDRIVER_PATH = "C:/tools/chromedriver_win32/chromedriver.exe";
    public static final String SITE_LINK = "https://hyperskill.org/";
    public static String JSON_PATH;
    public static String DATA_PATH;
    public static String FOLDER_PATH;

    public Util(int track) {
        JSON_PATH = "src/main/resources/answer-list-" + track + ".json";
        DATA_PATH = "src/main/resources/data-list-" + track + ".json";
        FOLDER_PATH = "C:/Users/Admin/Desktop/track/" + track + "/";
    }

    public void createDriver(boolean hide) {
        // Set path to browser driver
        System.setProperty("webdriver.chrome.driver", CHROMEDRIVER_PATH);
        ChromeOptions options = new ChromeOptions();

        // Create an instance of the driver in the background if "true"
        if (hide) {
            options.addArguments("--headless");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
        } else {
            options.addArguments("--start-maximized");
        }

        driver = new ChromeDriver(options);
    }

    // Perform authorization on the site
    public void login() {
        driver.get("https://hyperskill.org/login");

        waitDownloadElement("//input[@type='email']");

        WebElement emailField = driver.findElement(By.xpath("//input[@type='email']"));
        WebElement passwordField = driver.findElement(By.xpath("//input[@type='password']"));
        WebElement signInButton = driver.findElement(By.xpath("//button[@data-cy='submitButton']"));

        emailField.sendKeys("yakushevso@ya.ru");
        passwordField.sendKeys("{yx#e%B9~SGl4@Cr");
        signInButton.click();

        waitDownloadElement("//h1[@data-cy='curriculum-header']");
    }

    // Get track data and write to file
    public void getData(int track) {
        Topic topic = getTopics(track);
        List<Project> projects = getProjects(track);
        List<Step> steps = getSteps(topic);

        try (FileWriter writer = new FileWriter(DATA_PATH)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(new Data(topic, projects, steps), writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Get the list of topics
    private Topic getTopics(int track) {
        List<String> listTopic = new ArrayList<>();
        List<String> listDescendants = new ArrayList<>();

        int i = 1;
        boolean isNext = true;

        // While there is a next page, we loop
        while (isNext) {
            String url = "https://hyperskill.org/api/topic-relations?format=json&track_id=" + track +
                    "&page_size=100&page=" + i++ + "";

            // Get JSON object with data
            try (InputStream inputStream = new URL(url).openStream()) {
                JsonElement jsonElement = JsonParser.parseReader(new InputStreamReader(inputStream));
                JsonObject jsonObject = jsonElement.getAsJsonObject();

                // Check if there is a next data page
                if (!jsonObject.getAsJsonObject("meta").get("has_next").getAsBoolean()) {
                    isNext = false;
                }

                // Get an array of topics
                JsonArray topicRelationsArr = jsonObject.getAsJsonArray("topic-relations");

                for (JsonElement element : topicRelationsArr) {
                    JsonObject obj = element.getAsJsonObject();
                    listTopic.add(String.valueOf(obj.get("id")));

                    // Check if the topic is a parent
                    if (obj.get("parent_id").isJsonNull()) {
                        JsonArray descendantsArr = obj.getAsJsonArray("descendants");

                        // Get an array of child topics
                        for (JsonElement s : descendantsArr) {
                            listDescendants.add(String.valueOf(s));
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return new Topic(listTopic, listDescendants);
    }

    // Get a list of projects
    private List<Project> getProjects(int track) {
        List<Project> projectList = new ArrayList<>();

        String urlTrack = "https://hyperskill.org/api/tracks/" + track + "?format=json";

        // Get JSON object with data
        try (InputStream trackInputStream = new URL(urlTrack).openStream()) {
            JsonElement trackJsonElement = JsonParser.parseReader(new InputStreamReader(trackInputStream));
            JsonObject trackJsonObject = trackJsonElement.getAsJsonObject();

            // Get an array of projects
            JsonArray trackProjectsArray = trackJsonObject.getAsJsonArray("tracks");

            for (JsonElement projectElement : trackProjectsArray) {
                JsonObject projectObj = projectElement.getAsJsonObject();
                JsonArray projectArray = projectObj.getAsJsonArray("projects");

                for (JsonElement projectName : projectArray) {
                    String urlProject = "https://hyperskill.org/api/projects/" + projectName + "?format=json";

                    // Get JSON object with data
                    try (InputStream projectInputStream = new URL(urlProject).openStream()) {
                        JsonElement projectJsonElement = JsonParser.parseReader(new InputStreamReader(projectInputStream));
                        JsonObject projectJsonObject = projectJsonElement.getAsJsonObject();

                        // Get the project data array
                        JsonArray projectDataArray = projectJsonObject.getAsJsonArray("projects");

                        for (JsonElement projectElement1 : projectDataArray) {
                            JsonObject projectObj1 = projectElement1.getAsJsonObject();

                            int projectId = projectObj1.get("id").getAsInt();
                            String projectTitle = projectObj1.get("title").getAsString();
                            List<String> stagesIds = new ArrayList<>();

                            for (JsonElement stageId : projectObj1.getAsJsonArray("stages_ids")) {
                                stagesIds.add(String.valueOf(stageId));
                            }

                            projectList.add(new Project(projectId, projectTitle, stagesIds));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return projectList;
    }

    // Get a list of topics and tasks
    private List<Step> getSteps(Topic topics) {
        List<Step> steps = new ArrayList<>();

        for (String topic : topics.getDescendants()) {
            int i = 1;
            boolean isNext = true;

            // While there is a next page, we loop
            while (isNext) {
                String url = "https://hyperskill.org/api/steps?format=json&topic=" + topic +
                        "&page_size=100&page=" + i++ + "";

                driver.get(url);

                // Get page content as text
                String pageSource = driver.findElement(By.tagName("pre")).getText();

                // Get JSON object with data
                JsonElement jsonElement = JsonParser.parseString(pageSource);
                JsonObject jsonObject = jsonElement.getAsJsonObject();

                // Check if there is a next data page
                if (!jsonObject.getAsJsonObject("meta").get("has_next").getAsBoolean()) {
                    isNext = false;
                }

                int id = 0;
                String title = "";
                List<String> listStepTrue = new ArrayList<>();
                List<String> listStepFalse = new ArrayList<>();

                // Get an array of steps
                JsonArray topicRelationsArr = jsonObject.getAsJsonArray("steps");

                for (JsonElement element : topicRelationsArr) {
                    JsonObject obj = element.getAsJsonObject();

                    // Check the step type (theory or practice)
                    if (obj.get("type").getAsString().equals("theory")) {
                        // If the type is a theory, then get the theory ID and name
                        id = obj.get("topic_theory").getAsInt();
                        title = obj.get("title").getAsString();
                    } else if (obj.get("type").getAsString().equals("practice")) {
                        // Divide the lists into completed and uncompleted
                        if (obj.get("is_completed").getAsBoolean()) {
                            // If "practice", then add practice ID
                            listStepTrue.add(obj.get("id").getAsString());
                        } else {
                            listStepFalse.add(obj.get("id").getAsString());
                        }
                    }
                }

                steps.add(new Step(id, title, listStepTrue, listStepFalse));
            }
        }

        driver.quit();

        return steps;
    }

    // Get a list of objects from a file
    public static <T> T getFileData(Type type, String path) {
        Gson gson = new Gson();
        File file = new File(path);
        T result = null;

        if (file.exists() && file.length() != 0) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                JsonElement jsonElement = gson.fromJson(reader, JsonElement.class);

                if (jsonElement.isJsonArray()) {
                    // Read the list of objects
                    result = gson.fromJson(jsonElement, type);
                } else {
                    // Read single object
                    result = gson.fromJson(jsonElement.getAsJsonObject(), type);
                }

                reader.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return result;
    }

    // Save the object to a JSON file
    public static <T> void saveToFile(T answer, List<T> list, String path) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        File file = new File(path);

        // Append new data to existing ones in memory
        list.add(answer);

        // Write updated data to file
        try {
            FileWriter writer = new FileWriter(file);
            gson.toJson(list, writer);
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Check if the page has loaded
    public static boolean waitDownloadElement(String xpath) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        return wait.until(ExpectedConditions.and(
                ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)),
                ExpectedConditions.visibilityOfElementLocated(By.xpath(xpath)),
                ExpectedConditions.elementToBeClickable(By.xpath(xpath))
        ));
    }

    // Delay between transitions
    public static void delay(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
