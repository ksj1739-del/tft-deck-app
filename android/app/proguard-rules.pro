# R8 이 지우면 안 되는 것들.
#
# 이 앱에서 위험한 건 두 가지다:
#   1) kotlinx.serialization 은 컴파일러가 만든 Companion/serializer 를 리플렉션 없이 찾지만,
#      R8 이 쓰이지 않는 것으로 보고 지우면 파싱이 런타임에 터진다.
#   2) WorkManager 는 Worker 를 클래스 이름 문자열로 만든다. 이름이 바뀌면 동기화가 조용히 죽는다.

# ---------------------------------------------------------------- 직렬화
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisible*Annotations

# @Serializable 이 붙은 클래스와 그 생성 serializer
-keepclasseswithmembers class ** {
    @kotlinx.serialization.Serializable <fields>;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    static **$* *;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# 이 앱의 모델은 전부 직렬화 대상이라 통째로 남긴다 (26덱 규모라 용량 영향이 없다).
-keep class com.tftdeck.reader.data.** { *; }

# 게임 연동(ingame)의 지난 게임 로비·원격 플래그 모델은 data 패키지 밖에 있다.
# metatft 응답과 로비 캐시(filesDir/last_lobby.json)를 이 클래스들로 읽으므로 같은 이유로 남긴다.
-keep @kotlinx.serialization.Serializable class com.tftdeck.reader.ingame.** { *; }
-keep,includedescriptorclasses class com.tftdeck.reader.ingame.**$$serializer { *; }
-keepclassmembers class com.tftdeck.reader.ingame.** {
    *** Companion;
}
-keepclasseswithmembers class com.tftdeck.reader.ingame.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-dontnote kotlinx.serialization.**
-dontwarn kotlinx.serialization.**

# ---------------------------------------------------------------- WorkManager
-keep class com.tftdeck.reader.sync.DailySyncWorker { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ---------------------------------------------------------------- 오버레이
# ComposeView 를 서비스에 직접 붙이므로 ViewTree 오너 구현을 건드리지 않는다.
-keep class com.tftdeck.reader.overlay.OverlayService { *; }

# ---------------------------------------------------------------- 기타
-dontwarn org.slf4j.**
-dontwarn okhttp3.**

# 크래시 로그에서 줄 번호를 읽을 수 있게 남긴다.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
