# Minimal Koog Agent

A minimal standalone Kotlin example showing how to create and run a Koog agent with OpenAI.

## Requirements

- JDK 17 or later
- An OpenAI API key with available API quota

## Configure the API key

Set the key as an environment variable:

```bash
export OPENAI_API_KEY="your-api-key"
```

Do not commit API keys to the repository.

## Run the example

```bash
./gradlew run
```

## Build without calling the OpenAI API

```bash
./gradlew clean build
```

The agent asks for a concise explanation of why runtime isolation matters for AI agents.
