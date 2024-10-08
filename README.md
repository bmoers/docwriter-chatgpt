# AI Javadoc writer

Did you ever have the problem with this large legacy code base and wished for a good javadoc? Do you wish your fellow coders would have written a reasonable javadoc of their Java java code?

Just use the docwriter to update your application JavaDoc with AI generated documentation. 

## Eat my own dogfood:
Undocumented code
```java
@SpringBootApplication
@Slf4j
public class DocWriterApplication implements CommandLineRunner {

    ...

    @Override
    public void run(String ... args) throws Exception {
        logArgs();
		addDocs();

    }

}

```
Gets documented as
```java
/**
 *  The DocWriterApplication class is responsible for adding missing Javadoc to classes and interfaces in Java source code.
 *  It utilizes the OpenAI GPT-3.5 Turbo model to generate the Javadoc based on the provided source code.
 *  The Javadoc is added as class/interface-level comments and includes an author tag with the specified author name.
 *
 * @author DocWriterApplication
 */
@SpringBootApplication
@Slf4j
public class DocWriterApplication implements CommandLineRunner {

    ...

    @Override
    public void run(String ... args) throws Exception {
        logArgs();
		addDocs();

    }

}
```

## How to run this

Prerequisites:
* openai Api Key, as the usage of API not for free. You may get welcome credits thou to document your codebase: https://platform.openai.com/docs/quickstart/step-2-setup-your-api-key


## Docker
```bash

docker run \
-e OPENAI_API_KEY=your-openapi-key \
-v ./src/test/java:/data \
abaxsoraszem/docwriter:latest
```
This will produce class level documentation of the TestJavaClass.java file, which is part of this repo.

## Java
You may want to modify this for your own purposes. Since you are documenting a java application, a good understanding of running a java application is assumed. To run this application via Java

```bash
export OPENAI_API_KEY=your-openapi-key
./docWriter.sh --srcDir=./src/test/java 
```

You can provide any combination of the following parameters
```bash
./docWriter.sh --srcDir=./src/test/java --maxFileToChange=1 --classDoc=true --publicMethodDoc=true --nonPublicMethodDoc=true --author=yourname

```

This will traverse all the java files in the passed directory, and generate JavaDoc. Dont forget to proof read these docs and run your project formatter before propagating/comitting.

## Privacy consideration
Your code will be partially sent to OpenAI as part of the completion requests. Please note however, that OpenAI policy 
* excludes such data from training
* keeps the data ownership with you for both input (code) and output (docs)

https://openai.com/enterprise-privacy

## Blog Post
If you are interested in the details of the solution, read this [blog post](https://www.csabameszaros.com/2023/10/29/ai-javadoc-writer/)



