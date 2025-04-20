import json
import base64
import json
from pathlib import Path
import os
# Path to the JSON file
file_path = Path("events/event.json")

# Define the file paths (same directory as this script)
cert_path = Path(os.getenv("CERT_PATH"))
text_path = Path(os.getenv("FILE_PATH"))

# Function to read and encode a file as base64
def encode_file_to_base64(file_path):
    with open(file_path, "rb") as file:
        return base64.b64encode(file.read()).decode("utf-8")

# Load and encode files
encoded_certificate = encode_file_to_base64(cert_path)

encoded_text = encode_file_to_base64(text_path)
# New body content (this should be a JSON string if mimicking API Gateway)
new_body = json.dumps(
{"file1": encoded_text,
 "certificate":encoded_certificate,
 "secret": os.getenv("SECRET")
 }
)

# Load, modify, and save the file
if file_path.exists():
    with open(file_path, "r", encoding="utf-8") as file:
        event_data = json.load(file)

    # Update the 'body' field
    event_data['body'] = new_body

    # Save it back
    with open(file_path, "w", encoding="utf-8") as file:
        json.dump(event_data, file, indent=4)

    print("✅ 'body' field updated successfully.")
else:
    print(f"❌ File not found: {file_path}")
