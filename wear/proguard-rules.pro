# R8 / ProGuard rules for the Wear OS module.
#
# isMinifyEnabled is currently false in build.gradle.kts; these rules are kept
# in place so flipping minification on later does not silently strip the
# tile/complication/listener entry points that the system invokes by class name
# from the manifest (R8 sees them as unreferenced from app code).

# Wearable Data Layer payload classes referenced reflectively by play-services.
-keep class com.google.android.gms.wearable.** { *; }

# WearableListenerService subclasses are bound by the system via the manifest
# action com.google.android.gms.wearable.BIND_LISTENER. Keep all overrides.
-keep class * extends com.google.android.gms.wearable.WearableListenerService { *; }

# TileService subclasses are bound by the system via androidx.wear.tiles.action.BIND_TILE_PROVIDER.
-keep class * extends androidx.wear.tiles.TileService { *; }

# ComplicationDataSourceService subclasses are bound by the watch face via
# android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST.
-keep class * extends androidx.wear.watchface.complications.datasource.ComplicationDataSourceService { *; }
-keep class * extends androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService { *; }

# ProtoLayout / Tiles use reflection on builders and parcelables when crossing
# the binder; conservative keep so layout serialization isn't stripped.
-keep class androidx.wear.protolayout.** { *; }
-keep class androidx.wear.tiles.** { *; }
-keep class androidx.wear.watchface.complications.** { *; }
