# <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Travel%20and%20places/Fire.png" alt="Fire" width="35" height="35" />glyph-syncronator

## 🌐 Read this in other languages: 🇬🇧 [English](README.md) | 🇹🇷 [Türkçe](README_TR.md)

## <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Smilies/Partying%20Face.png" alt="Partying Face" width="25" height="25" /> Android ऐप यहाँ है!
हमने सफलतापूर्वक एक साधारण Python स्क्रिप्ट से एक शक्तिशाली Android ऐप में बदलाव किया है! यह आपके डिवाइस से लाइव ऑडियो स्ट्रीम को **Media Projection** का उपयोग करके पकड़ता है और सीधे उसे glyphs में प्रोसेस करता है। इसका मतलब है कि आप **Spotify, YouTube Music** और लगभग किसी भी अन्य ऐप से म्यूजिक विज़ुअलाइज़ कर सकते हैं, बिना किसी मैनुअल प्रोसेसिंग के! अब सिर्फ लोकल फाइल्स तक सीमित नहीं!

## <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Smilies/Thinking%20Face.png" alt="Thinking Face" width="25" height="25" /> यह क्यों बनाया गया?
बहुत से लोगों के लिए (मेरे सहित), *Nothing द्वारा दिया गया स्टॉक Glyph Music Visualization* काफी रैंडम लगता है।  
भले ही तकनीकी रूप से ऐसा न हो, लेकिन म्यूजिक के साथ विजुअल रिस्पॉन्स उतना स्पष्ट नहीं होता। इसके अलावा, यह फीचर Glyph Interface की पूरी क्षमता का उपयोग नहीं करता। इसलिए मैंने अपना खुद का म्यूजिक विज़ुअलाइज़र बनाया।

## <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/2696_fe0f/512.gif" alt="⚖" width="32" height="32"> Stock vs Better Music Visualizer
| Feature | Nothing Stock | **Better Music Visualizer** |
| :--- | :--- | :--- |
| **Light levels** | ~2-bit depth (3 light levels) | **12-bit depth (4096 light levels)** |
| **Frame Rate** | ~25 FPS | **60 FPS** |
| **Precision** | रैंडम जैसा लगता है, यह समझना मुश्किल है कि यह कैसे सिंक हो रहा है | **FFT analysis का उपयोग करके हर लाइट की intensity को सटीक रूप से निर्धारित करता है** |
| **Zones** | स्टैंडर्ड, पूरे physical glyphs का उपयोग | **हर glyph segment और sub-zone को अलग-अलग कंट्रोल किया जाता है** |
| **Visualisation method** | केवल real-time | **Realtime (20ms latency तक), या pre-processed audio files** |

## 📲 Supported Nothing Phone Models
वर्तमान में ये मॉडल सपोर्टेड हैं:
- Nothing phone (1) 
  - ऐप के लिए glyph debug mode **ON** होना चाहिए, इसे *ADB command* से सेट करें: `adb shell settings put global nt_glyph_interface_debug_enable 1`.
- Nothing phone (2)
- Nothing phone (2a)
- Nothing phone (2a plus)
- Nothing phone (3a)
- Nothing phone (3a pro)
- *Nothing phone (3)* **(beta, अभी अच्छा नहीं है)**
- *Nothing Phone (4a)*
- *Nothing Phone (4b)*

## 📖 ऐप का उपयोग कैसे करें?
1. **Latest APK डाउनलोड करें**
2. **Permissions दें**
3. **Start दबाएं और म्यूजिक चलाएं**
4. **Latency adjust करें**
5. **Presets बदलें**

## 🏗️ Contributing
Contributions का स्वागत है!

## 🔒 Security
https://www.virustotal.com/gui/url/c92c1ff82b56eb60bfd1e159592d09f949f0ea2d195e01f7f5adbef0e0b0385b?nocache=1
