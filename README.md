# ArtemisBleApp
An Android Application (Cellphone) connect with Artemis Nano via bluetooth connection (UART). 

Artemis Nano act as a server passively waiting to be connected from Android devices (Clients).
This Android App could send messages using the UART service. Messages include three options, **text**, **gyroscope reading**, and two virtual **joystick reading**. 

For more information, please refer links in credits and SparkFun Artemis Forum https://forum.sparkfun.com/viewforum.php?f=167. 

## How to use this repo:
1. Download this repo and install the apk to your Android device. Or open this repo with Android Studio and upload the code. 
2. Extract the Arduino code from Viper2_7.zip or open the Viper2_7 directory with Arduino IDE. Upload the Arduino code to your Artemis Nano. 

## Must do:
Android App requires Location to be manually enabled OR YOU WILL NOT SEE any devices when you scan. Enable Location permission under settings / apps & notifications ... Permissions/Location (by Kerryeven)

## Credits:
1. This Github is modified from Kerryeven / AndroidArtemisBleUartClient 
   https://github.com/kerryeven/AndroidArtemisBleUartClient
2. Joystick design from  efficientisoceles / JoystickView https://github.com/efficientisoceles/JoystickView
3. Inspired by  paulvha / apollo3  https://github.com/paulvha/apollo3
