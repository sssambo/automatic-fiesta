# #!/bin/bash

# # Exit immediately if a command exits with a non-zero status
# set -e

# echo "==========================================="
# echo "🚀 Starting Automated Android SDK Setup..."
# echo "==========================================="

# # 1. Update and install Java 17 (Required for modern Gradle versions)
# echo "📦 Updating packages and installing OpenJDK 17..."
# sudo apt-get update && sudo apt-get install -y openjdk-17-jdk unzip wget

# # 2. Setup Android SDK Directory Paths
# echo "📂 Creating Android SDK folder layout..."
# ANDROID_DIR="$HOME/android-sdk"
# mkdir -p "$ANDROID_DIR/cmdline-tools"
# cd "$ANDROID_DIR/cmdline-tools"

# # 3. Download official Google Linux Command Line Tools package
# echo "📥 Downloading official Android Command Line Tools..."
# CMD_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
# wget -q --show-progress "$CMD_TOOLS_URL" -O cmdtools.zip

# # 4. Extract and fix the nesting architecture Gradle requires ("latest")
# echo "📦 Extracting files..."
# unzip -q cmdtools.zip
# rm cmdtools.zip
# mv cmd-tools latest

# 5. Export environment variables to .bashrc permanently if they don't exist
echo "✏️ Configuring environment paths in ~/.bashrc..."
if ! grep -q "ANDROID_HOME" ~/.bashrc; then
    echo 'export ANDROID_HOME=$HOME/android-sdk' >> ~/.bashrc
    echo 'export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin' >> ~/.bashrc
    echo 'export PATH=$PATH:$ANDROID_HOME/platform-tools' >> ~/.bashrc
fi

# Load variables for the current running shell script context
export ANDROID_HOME="$HOME/android-sdk"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"

# 6. Automatically accept SDK Licenses and install components
echo "📄 Accepting licenses and downloading platform tools..."
# Automatically feeds "yes" to all license questions
yes | sdkmanager --licenses > /dev/null

echo "📥 Fetching platforms;android-29 and build-tools..."
sdkmanager "platforms;android-29" "build-tools;29.0.3" "platform-tools"

# 7. Auto-configure the local project properties target file
# Finds the active project workspace root directory in codespaces dynamically
WORKSPACE_DIR=$(find /workspaces -maxdepth 1 -mindepth 1 -type d | head -n 1)

if [ -d "$WORKSPACE_DIR" ]; then
    echo "⚙️ Linking SDK directly to project local.properties at $WORKSPACE_DIR..."
    echo "sdk.dir=/home/vscode/android-sdk" > "$WORKSPACE_DIR/local.properties"
else
    echo "⚠️ Workspace folder could not be auto-detected. Please manually add 'sdk.dir=/home/vscode/android-sdk' to your project's local.properties file."
fi

echo "==========================================="
echo "✅ Setup Complete!"
echo "🔄 RUN: 'source ~/.bashrc' to activate tools in this window."
echo "==========================================="
