package abax.docwriter.DocWriter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.CompilationUnit.Storage;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.javadoc.Javadoc;
import com.github.javaparser.javadoc.JavadocBlockTag;
import com.github.javaparser.javadoc.description.JavadocDescription;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.utils.SourceRoot;

import lombok.extern.slf4j.Slf4j;

/**
 * The TestJavaClass class is a Spring Boot application that implements the CommandLineRunner interface. 
 * It is responsible for analyzing Java source code and adding missing Javadoc for classes and methods based on specified configurations.
 * 
 * This application accepts the following command line arguments:
 * - srcDir: The directory path to the source code. Default value is an empty string.
 * - author: The author name to be included in the Javadoc. Default value is "DocWriterApplication".
 * - maxFileToChange: The maximum number of files to apply the Javadoc changes. Default value is 1.
 * - classDoc: Flag to indicate whether to add Javadoc for classes/interfaces. Default value is true.
 * - publicMethodDoc: Flag to indicate whether to add Javadoc for public methods. Default value is false.
 * - nonPublicMethodDoc: Flag to indicate whether to add Javadoc for non-public methods. Default value is false.
 * 
 * The TestJavaClass application makes use of the OpenAI GPT-3.5 Turbo model to generate Javadoc. 
 * The access token for the API should be set as the OPENAI_API_KEY environment variable.
 * 
 * To run the application, simply execute the main method.
 * @author DocWriterApplication
*/
@SpringBootApplication
@Slf4j
public class DocWriterApplication implements CommandLineRunner {

    @Value("${srcDir:}")
    private String srcDir;

    @Value("${author:DocWriterApplication}")
    private String author;

    @Value("${maxFileToChange:1}")
    private int maxFileToChange;

    private String openaiApiKey = System.getenv("OPENAI_API_KEY");

    @Value("${classDoc:true}")
    private boolean classDoc;

    @Value("${publicMethodDoc:false}")
    private boolean publicMethodDoc;

    @Value("${nonPublicMethodDoc:false}")
    private boolean nonPublicMethodDoc;

    public static void main(String[] args) {
        SpringApplication.run(DocWriterApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        logArgs();
        addDocs();

    }

    private void logArgs() {
        log.info("srcDir: " + srcDir);
        log.info("maxFileToChange: " + maxFileToChange);
        log.debug("openApiToken: " + openaiApiKey);
        log.info("classDoc: " + classDoc);
        log.info("publicMethodDoc: " + publicMethodDoc);
        log.info("nonPublicMethodDoc: " + nonPublicMethodDoc);
    }

    private void addDocs() throws Exception {

        // Create a SourceRoot
        SourceRoot sourceRoot = new SourceRoot(Paths.get(srcDir));

        // Parse all the Java files in the source root
        List<ParseResult<CompilationUnit>> parseResults = sourceRoot.tryToParse("");

        int changeCounter = maxFileToChange;

        // Analyze each parsed result
        for (ParseResult<CompilationUnit> parseResult : parseResults) {
            if (parseResult.isSuccessful()) {
                CompilationUnit cu = parseResult.getResult().get();
                // this will try to preserve the format
                LexicalPreservingPrinter.setup(cu);

                log.info("Processing " + cu.getStorage().get().getPath());
                boolean fileChanged = false;

                // process only the top element of the file
                ClassOrInterfaceDeclaration topClassOrInterface = cu
                        .findFirst(ClassOrInterfaceDeclaration.class).get();
                if (topClassOrInterface.getComment().isEmpty() && this.classDoc) {
                    addClassJavadoc(cu, topClassOrInterface);
                    fileChanged = true;
                }

                // Process public methods if the top-level element is a class
                if (this.publicMethodDoc || this.nonPublicMethodDoc) {
                    if (!topClassOrInterface.isInterface()) {
                        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
                            if (method.getComment().isEmpty()
                                &&
                             ((method.isPublic() && this.publicMethodDoc)
                             ||
                             (!method.isPublic() && this.nonPublicMethodDoc))) {
                                addMethodJavadoc(cu, method);
                                fileChanged = true;
                            }
                        }
                    }
                }
                if (fileChanged){
                    changeCounter--;
                    save(cu);

                }

            }
            if (changeCounter <= 0) {
                return;
            }
        }
    }

    private void addMethodJavadoc(CompilationUnit cu, MethodDeclaration method) throws Exception {
        log.info("Adding missing JavaDoc for method: " + method.getNameAsString());

        String sourceCode = method.toString();
        String generatedJavadoc = generateJavaDoc(sourceCode, setupMethodDocGeneration());
        if (generatedJavadoc == null){
            log.error("Failed to generate javadoc for {}", method.getNameAsString());
            return;
        }
        Javadoc javadoc = new Javadoc(JavadocDescription.parseText(generatedJavadoc));
        JavadocComment javadocComment = new JavadocComment(javadoc.toText());
        method.setJavadocComment(javadocComment);

    }

    private void addClassJavadoc(CompilationUnit cu, ClassOrInterfaceDeclaration classOrInterface) throws Exception {

        log.info("Adding missing JavaDoc for class/interface: " + classOrInterface.getNameAsString());

        String sourceCode = "";
        for (Node child : cu.getChildNodes()) {
            if (!(child instanceof com.github.javaparser.ast.ImportDeclaration)) {
                sourceCode += child.toString();
            }
        }

        String generatedJavadoc = generateJavaDoc(sourceCode, setupClassDocGeneration());
        if (generatedJavadoc == null){
            log.error("Failed to generate javadoc for {}", classOrInterface.getNameAsString());
            return;
        }
        Javadoc javadoc = new Javadoc(JavadocDescription.parseText(generatedJavadoc));
        javadoc.addBlockTag(new JavadocBlockTag(JavadocBlockTag.Type.AUTHOR, author));
        JavadocComment javadocComment = new JavadocComment(javadoc.toText());

        classOrInterface.setJavadocComment(javadocComment);

    }

    private void save(CompilationUnit cu) throws IOException {
        Optional<Storage> storage = cu.getStorage();

        if (storage.isPresent()) {
            Path pathToFile = storage.get().getPath();
            log.info("Writing docs to " + pathToFile);


        
            String code = LexicalPreservingPrinter.print(cu);
            Files.write(pathToFile, code.getBytes(StandardCharsets.UTF_8));

        }

    }

    private String generateJavaDoc(String classSourceCode, JSONObject messageBody) throws Exception {
        log.trace("generating javadoc for \n" + classSourceCode);



        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", classSourceCode);
        messageBody.getJSONArray("messages").put(userMessage);

        messageBody.put("model", "gpt-3.5-turbo");
        messageBody.put("temperature", 1);
        messageBody.put("max_tokens", 256);
        messageBody.put("top_p", 1);
        messageBody.put("frequency_penalty", 0);
        messageBody.put("presence_penalty", 0);

        log.info("Sending ChatGpt request ");

        // Create HTTP request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + openaiApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(messageBody.toString(), StandardCharsets.UTF_8))
                .build();

        // Send the request
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        log.debug("Received response {}", response.body());

        // Process the response
        JSONObject jsonResponse = new JSONObject(response.body());
        if (jsonResponse.has("error")){
            throw new RuntimeException(jsonResponse.getJSONObject("error").toString());

        }
        String content = jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                .getString("content");
        log.info("ChatGpt response received: ");
        String classJavaDoc = extractJavadoc(content);
        log.info(classJavaDoc);

        return classJavaDoc;

    }

    private JSONObject setupMethodDocGeneration() {
        JSONArray messagesArray = new JSONArray();

        JSONObject systemMessage = new JSONObject();
        systemMessage.put("role", "system");
        systemMessage.put("content",
                "You will be provided with java source code. Your task is to generate javadoc for this java method. \nDo not generate code comments.\nDo not print out the source code, that has been provided as input, merely the Javadoc for the method starting with the \n/**\nand ending with the\n/*\n");
        messagesArray.put(systemMessage);

        JSONObject messageBody = new JSONObject();
        messageBody.put("messages", messagesArray);

        return messageBody;    }

    private JSONObject setupClassDocGeneration() {
        JSONArray messagesArray = new JSONArray();

        JSONObject systemMessage = new JSONObject();
        systemMessage.put("role", "system");
        systemMessage.put("content",
                "You will be provided with the source code of a java class or java interface. Your task is to generate javadoc for this class or interface. The javadoc must be generated for the class or interface level, and not on the method level. \\n" + //
                        "Do not generate code comments.\\n" + //
                        "Do not print out the source code, that has been provided as input.");
        messagesArray.put(systemMessage);

        JSONObject sampleUserMessage = new JSONObject();
        sampleUserMessage.put("role", "user");
        sampleUserMessage.put("content",
                "public class JavaDocAnalyzer {\n\n    private void main(String[] args) throws IOException {\n        // Define the path to the source code\n        String pathToSrc = \"src/main/java\";\n\n        // Create a SourceRoot\n        SourceRoot sourceRoot = new SourceRoot(Paths.get(pathToSrc));\n\n        // Parse all the Java files in the source root\n        List<ParseResult<CompilationUnit>> parseResults = sourceRoot.tryToParse(\"\");\n\n        // Analyze each parsed result\n        for (ParseResult<CompilationUnit> parseResult : parseResults) {\n            if (parseResult.isSuccessful()) {\n                CompilationUnit cu = parseResult.getResult().get();\n                checkForMissingJavaDoc(cu);\n            }\n        }\n    }\n\n    private static void checkForMissingJavaDoc(CompilationUnit cu) throws Exception {\n        for (ClassOrInterfaceDeclaration classOrInterface : cu.findAll(ClassOrInterfaceDeclaration.class)) {\n            // Check if the class/interface has JavaDoc\n            if (!classOrInterface.getComment().isPresent()) {\n                log.info(\"Adding missing JavaDoc for class/interface: \" + classOrInterface.getNameAsString());\n                \n                String sourceCode = \"\";\n                for (Node child : cu.getChildNodes()) {\n                    if (!(child instanceof com.github.javaparser.ast.ImportDeclaration)) {\n                        sourceCode += child.toString();\n                    }\n                }\n                \n                // Get the source code of the class without imports and pass it to generateJavaDoc\n                // String classSourceCode = cu.toString();\n                generateJavaDoc(sourceCode);\n            }\n        }\n    }\n    \n    private static void generateJavaDoc(String classSourceCode) throws Exception {\n        log.info(\"generating javadoc for \\n\" + classSourceCode);\n\n        // Prepare the request\n        HttpClient client = HttpClient.newHttpClient();\n    \n        // Prepare the body\n        JSONObject messageBody = new JSONObject();\n        messageBody.put(\"model\", \"gpt-3.5-turbo\");\n        messageBody.put(\"messages\", List.of(\n            Map.of(\"role\", \"system\", \"content\", \"You are a javadoc documentation writer reading the source code, and outputting javadoc only.\"),\n            Map.of(\"role\", \"user\", \"content\", classSourceCode)\n        ));\n    \n        // Create HTTP request\n        HttpRequest request = HttpRequest.newBuilder()\n                .uri(new URI(\"https://api.openai.com/v1/chat/completions\"))\n                .header(\"Content-Type\", \"application/json\")\n                .header(\"Authorization\", \"Bearer \" + \"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\")\n                .POST(HttpRequest.BodyPublishers.ofString(messageBody.toString(), StandardCharsets.UTF_8))\n                .build();\n    \n        // Send the request\n        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());\n    \n        // Process the response\n        JSONObject jsonResponse = new JSONObject(response.body());\n        String javaDoc = jsonResponse.getJSONArray(\"choices\").getJSONObject(0).getString(\"message\").getJSONObject(\"content\");\n        log.info(javaDoc); // or add it to the class or take other actions\n    \n        System.exit(0);\n    }\n    \n}");
        messagesArray.put(sampleUserMessage);

        JSONObject sampleAssistantMessage = new JSONObject();
        sampleAssistantMessage.put("role", "assistant");
        sampleAssistantMessage.put("content",
                "/**\n * The JavaDocAnalyzer class is responsible for analyzing Java source code and generating Javadoc for classes\n * and interfaces that are missing it.\n */");
        messagesArray.put(sampleAssistantMessage);

        JSONObject messageBody = new JSONObject();
        messageBody.put("messages", messagesArray);

        messageBody.put("stop", new JSONArray(List.of("public class", "public interface"))); // stop it passed code start to be outputted

        return messageBody;
    }

    private String extractJavadoc(String input) {
        int startIndex = input.indexOf("/**");
        int endIndex = input.indexOf("*/");

        if (startIndex != -1 && endIndex != -1) {
            return input.substring(startIndex + 3, endIndex);
        } else {
            return null;
        }
    }
}
