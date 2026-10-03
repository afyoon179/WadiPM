# داشبورد گروه صنعتی وادی — اپلیکیشن اندروید

اپلیکیشن رسمی اندروید برای داشبورد نگهداری پیشگیرانه (PM) گروه صنعتی وادی.

آدرس سرویس:  
https://wadi-steel-industrial-group.wadi-steel200.workers.dev/

## ویژگی‌ها

- اجرای کامل داشبورد وب داخل اپ (WebView)
- پشتیبانی از ورود کاربران و ذخیره session
- آپلود فایل Excel / Word برای ایمپورت فعالیت‌های PM
- پشتیبانی کامل RTL و فارسی
- نوار پیشرفت هنگام بارگذاری
- صفحه خطا در صورت قطع اینترنت + دکمه تلاش مجدد
- دکمه بازگشت دو مرحله‌ای برای خروج
- تم تیره هماهنگ با داشبورد

## پیش‌نیاز

- Android Studio Hedgehog (2023.1.1) یا جدیدتر
- JDK 17
- minSdk 24 (اندروید ۷ به بالا)

## نحوه ساخت و اجرا

1. پوشه `WadiPMApp` را در Android Studio باز کنید  
   (File → Open → انتخاب پوشه WadiPMApp)

2. صبر کنید تا Gradle Sync کامل شود.

3. یک دستگاه یا شبیه‌ساز اندروید انتخاب کنید و Run بزنید.

### ساخت APK برای نصب

```bash
./gradlew assembleRelease
```

فایل خروجی:
```
app/build/outputs/apk/release/app-release-unsigned.apk
```

برای امضا کردن APK می‌توانید از Build → Generate Signed Bundle / APK در Android Studio استفاده کنید.

## ساختار پروژه

```
WadiPMApp/
├── app/
│   ├── src/main/
│   │   ├── java/com/wadisteel/pm/
│   │   │   └── MainActivity.kt      ← منطق اصلی WebView
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml
│   │   │   ├── values/...
│   │   │   └── ...
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

## نکات مهم

- اپ نیاز به اینترنت دارد و داده‌ها روی Cloudflare D1 ذخیره می‌شوند.
- برای آپلود فایل، دسترسی به انتخاب‌گر فایل سیستم (SAF) استفاده می‌شود.
- اگر Worker شما تغییر آدرس داد، فقط ثابت `DASHBOARD_URL` در `MainActivity.kt` را عوض کنید.

## سازنده داشبورد وب

افشین موسی‌وند
