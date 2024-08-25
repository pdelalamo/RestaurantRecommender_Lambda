Fetches restaurant menu items based on user-provided nutritional goals (e.g., protein, carbs, fat, energy).
Uses OpenAI's GPT API to generate recommendations.
Retrieves secure parameters (API keys, model settings) from AWS SSM Parameter Store.
Handles HTTP requests and responses using Spring WebFlux.

Configuration
Before deploying the Lambda function, ensure the following parameters are stored in AWS SSM Parameter Store:

OpenAI API Key:

Name: OpenAI-API_Key_Encrypted
Description: Encrypted OpenAI API key for accessing GPT-3/4.
Type: SecureString
OpenAI Model Name:

Name: OpenAI-Model
Description: The GPT model name (e.g., gpt-3.5-turbo).
Model Temperature:

Name: OpenAI-Model-Temperature
Description: The temperature setting for GPT (e.g., 0.7).
Max Tokens:

Name: OpenAI-Max-Tokens
Description: Maximum tokens to use in a response (e.g., 150).

Deployment
Create an AWS Lambda Function:

Go to the AWS Management Console and create a new Lambda function.
Upload the JAR file created in the target/ directory.
Set up IAM Role:

Ensure the Lambda function has an IAM role with the following permissions:

ssm:GetParameter
ssm:GetParameters

Usage
This Lambda function expects an input with query parameters that include:

pdf: URL of the restaurant menu in PDF format.
mealTime: Meal time (e.g., Breakfast, Lunch, Dinner).
protein: Target protein intake.
carbs: Target carbohydrates intake.
fat: Target fat intake.
targetEnergy: Target energy intake.
energyUnit: Unit of energy (e.g., kcal).
weightUnit: Unit of weight (e.g., g).

The Lambda function will return a JSON array with the recommended menu items that best match the nutritional targets.
