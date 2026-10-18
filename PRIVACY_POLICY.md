# Privacy Policy for WallFlow

**Effective Date:** October 18, 2026  
**Developer:** ElectroFierro / Luis Andrade

WallFlow ("the App") is designed with user privacy as a fundamental priority. This Privacy Policy describes how WallFlow handles your information.

## 1. Information Collection and Use
* **No Personal Data Collected:** WallFlow does **NOT** collect, store, transmit, or share any personal identifying information (such as your name, email, phone number, location, contacts, or device identifiers).
* **Local Processing:** All wallpaper operations, folder scanning, image filtering, dimming, and scheduling take place strictly on your device.
* **Storage Access (SAF):** The App requests access only to the specific folders you choose via the Android Storage Access Framework (SAF) in order to set them as wallpapers. We do not read or access any other files on your device.
* **Internet Access:** If you enable the automatic wallpaper downloader feature, the App uses the internet solely to fetch public vertical images from the Wallhaven API according to your search keywords. No user data is sent with these requests.

## 2. Permissions Used
* **`SET_WALLPAPER`**: To apply wallpapers to your Home and Lock screens.
* **`FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_DATA_SYNC`**: To monitor screen lock/unlock events and change wallpapers seamlessly when configured by the user.
* **`RECEIVE_BOOT_COMPLETED`**: To resume scheduled wallpaper rotation after your device restarts.
* **`POST_NOTIFICATIONS`**: To display the persistent foreground service notification required by Android.
* **`INTERNET`**: To download public wallpapers if the downloader feature is enabled.

## 3. Third-Party Services
WallFlow does not integrate with any third-party advertising networks, analytics providers, or tracking SDKs.

## 4. Children's Privacy
The App does not collect any information from anyone, including children under the age of 13.

## 5. Contact
If you have any questions or suggestions regarding this Privacy Policy, please contact:
* **Email:** andradeluisg@gmail.com
