# 🎵 Songify - Modern Android Music Streaming App

Songify is a feature-rich, bug-free Android music streaming application built in **Kotlin** and **Jetpack Compose** using modern Android development best practices. It streams high-quality music from the JioSaavn API, features intelligent on-device recommendations, supports background playback with **HyperOS Island / Lock Screen / Notification** controls, and offers a gorgeous Material 3 UI.

---

## ✨ Key Features & Architecture

### 1. 🏠 Home Screen & Discovery
- **✨ Recommended For You**: An on-device preference algorithm tracks your search queries and listened tracks to dynamically curate personalized music recommendations.
- **⏳ Previously Listened**: Keeps track of your listening history along with exact timestamps (date and time stored).
- **❤️ Liked Songs**: Save your favorite tracks with an animated heart button and access them instantly from the home screen.
- **Regional & Language Hits**: Categorized sections for Telugu Hits, Hindi Hits, Tamil Melodies, and Punjabi Party.

### 2. 🔍 Instant Search
- **Live Search**: Automatically focuses the search bar and shows the soft keyboard on open, querying songs in real time from the JioSaavn API.
- **Resilient Network Handling**: Fully handles slow signals or connection losses with friendly error banners and instant **Retry** buttons.

### 3. 🎧 Advanced Audio Player & Queue
- **Seamless Streaming**: Powered by Android's `MediaPlayer` with WakeLock (`PARTIAL_WAKE_LOCK`) and Audio Focus management to ensure stable playback across screen locking and unlocking.
- **Queue & Playback Controls**: Full queue support with **Skip Next**, **Skip Previous**, **Shuffle Mode**, and **Loop / Repeat Mode**.
- **Sleep Timer**: Set a sleep timer (15, 30, 45, or 60 minutes) to automatically stop music playback.

### 4. 💫 Max Player View & UI Polish
- **Immersive Expanded Player**: Tapping the mini-player opens the Max Player View featuring a gradient background, elevated album art card, interactive progress slider, and song timestamps.
- **Spring-Animated Like Button**: Heart button pops with bouncy spring physics (`animateFloatAsState`) and transitions to a glowing pink/red (`#FF2D55`).
- **Song Details Dialog**: View detailed track metadata including album name, release year, language, record label, and play count.
- **Listening History Tab**: Dedicated history screen showing all listened songs with timestamps.

### 5. 🚀 System Integration & HyperOS Island
- **MediaSessionCompat & MediaStyle**: Integrated with system media frameworks to support continuous background playback controls on the lock screen, notification center, and **Xiaomi HyperOS Island (Capsule)** and it is also made to work with all dynamic island implementations in all operating systems.
- **Direct Notification Launch**: Tapping the music notification in your notification center brings the app to the foreground and automatically opens the Max Player View for the active track.

---

## 🛠️ Tech Stack & Libraries
- **Language**: Kotlin
- **UI Toolkit**: Jetpack Compose & Material 3
- **Architecture**: MVVM (AndroidViewModel, Kotlin Coroutines, StateFlow)
- **Networking**: Retrofit 2, Gson, OkHttp Logging Interceptor
- **Image Loading**: Coil Compose (`coil-compose`)
- **Media & Notifications**: Android `MediaPlayer`, `MediaSessionCompat`, `NotificationCompat.MediaStyle`

---

## 🚀 Getting Started
1. Open the project in **Android Studio**.
2. Sync project with Gradle files.
3. Run the app on an Android emulator or physical device (Requires Android 6.0 / API 23 or higher).
