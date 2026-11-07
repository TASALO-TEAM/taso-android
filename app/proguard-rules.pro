# R8 (release). El parseo usa JsonElement (sin clases @Serializable), así que basta con Retrofit.
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep interface com.tasalo.android.data.remote.TasaloApi { *; }
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
# Puente window.TasaloAndroid de la pestana Tienda: R8 no debe renombrar ni quitar los metodos que llama la web.
-keepclassmembers class com.tasalo.android.ui.tienda.TiendaBridge { @android.webkit.JavascriptInterface <methods>; }
