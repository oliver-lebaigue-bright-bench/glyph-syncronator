# <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Travel%20and%20places/Fire.png" alt="Fire" width="35" height="35" />glyph-syncronator
<img 
  src="https://img.shields.io/github/downloads/oliver-lebaigue-bright-bench/glyph-syncronator/total?style=for-the-badge&logo=github&label=Total%20app%20downloads%20from%20github:&color=ff0000&labelColor=000000"
  style="height:40px;">
  
[<img height="80" alt="Get it on GitHub" src="./.github/assets/get-it-on-github.png" />](https://github.com/oliver-lebaigue-bright-bench/glyph-syncronator/releases)
   
# नवीन परवाना, कृपया वाचा 
## 🌐 Read this in other languages:   🇬🇧 [English](README.md) | 🇮🇳 [हिन्दी](README_HI.md) | 🇹🇷 [Türkçe](README_TR.md)

## <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Smilies/Partying%20Face.png" alt="Partying Face" width="25" height="25" /> Android App आला आहे!
आम्ही साध्या Python script मधून एका शक्तिशाली Android app कडे यशस्वीरित्या स्थलांतरित झालो आहोत! हे तुमच्या डिव्हाइसमधून **Media Projection** वापरून थेट ऑडिओ स्ट्रीम घेते आणि ते थेट glyphs मध्ये प्रक्रिया करते. याचा अर्थ तुम्ही **Spotify, YouTube Music** आणि इतर कोणत्याही app मधील संगीत मॅन्युअल प्रक्रियेशिवाय visualize करू शकता! आता फक्त local files पुरतेच मर्यादित नाही!

## <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Smilies/Thinking%20Face.png" alt="Thinking Face" width="25" height="25" /> हे का अस्तित्वात आहे?
अनेक लोकांना (माझ्यासह), *Nothing ने दिलेले stock Glyph Music Visualisation* यादृच्छिक वाटते.  
तांत्रिकदृष्ट्या ते तसे नसले तरी, संगीताला व्हिज्युअल प्रतिसाद फारसा स्पष्ट नाही. त्यावर, हे फीचर Glyph Interface च्या पूर्ण क्षमतेचा वापर करत नाही. म्हणूनच मी स्वतःचा music visualizer बनवला.

## <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/2696_fe0f/512.gif" alt="⚖" width="32" height="32"> Stock vs Better Music Visualizer
| फीचर | Nothing Stock | **Better Music Visualizer** |
| :--- | :--- | :--- |
| **प्रकाश पातळी** | ~2-bit depth (3 प्रकाश पातळ्या) | **12-bit depth (4096 प्रकाश पातळ्या)** |
| **Frame Rate** | ~25 FPS | **60 FPS** |
| **अचूकता** | यादृच्छिक वाटते, sync कसा आहे ते समजणे कठीण | **प्रत्येक प्रकाशाची तीव्रता अचूकपणे ठरवण्यासाठी FFT analysis वापरते** |
| **Zones** | Standard, पूर्ण physical glyphs वापरल्या जातात | **प्रत्येक glyph segment आणि sub-zone स्वतंत्रपणे वापरला आणि नियंत्रित केला जातो** |
| **Visualisation पद्धत** | फक्त Real-time | **20ms latency सह Realtime, किंवा pre-processed audio files** |

## <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/1f3ac/512.gif" alt="🎬" width="40" height=""> [व्हिडिओ demos आणि उदाहरणे](https://github.com/oliver-lebaigue-bright-bench/glyph-syncronator/blob/main/Demo-video-examples.md)

### फरक प्रत्यक्षात पाहा! [**आमचे व्हिडिओ demos सहज browse करण्यासाठी येथे क्लिक करा!**](https://github.com/oliver-lebaigue-bright-bench/glyph-syncronator/blob/main/Demo-video-examples.md)

## 📲 समर्थित Nothing Phone मॉडेल्स
सध्या हे मॉडेल्स समर्थित आहेत:
- Nothing phone (1) 
  - Needs glyph debug mode **ON** for the app, set through an *ADB command*: `adb shell settings put global nt_glyph_interface_debug_enable 1`. This will be fixed once Nothing gives us their API key.
- Nothing phone (2)
- Nothing phone (2a)
- Nothing phone (2a plus)
- Nothing phone (3a)
- Nothing phone (3a pro)
- Nothing Phone (4a)

**partial support:**
- Nothing Phone (4a) pro
- Nothing phone (3) 

### <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/2699_fe0f/512.gif" alt="⚙" width="25" height="25"> हे तांत्रिकदृष्ट्या कसे कार्य करते
- उच्च दर्जाचा audio stream कॅप्चर केला जातो
- प्रत्येक **16.666 ms frame** (60 FPS) साठी **20 ms window** मध्ये frequencies चे विश्लेषण करण्यासाठी **FFT (Fast Fourier Transform)** वापरला जातो, ज्यामुळे visualization अधिक अचूक होते
- प्रत्येक glyph zone साठी **Frequency ranges** `zones.config` मध्ये परिभाषित केल्या आहेत आणि पूर्णपणे सानुकूल करण्यायोग्य आहेत.
- प्रत्येक glyphची **brightness** त्याच्या नियुक्त frequency range मध्ये आढळलेल्या **peak magnitude** द्वारे निर्धारित केली जाते  
  हे वेगवेगळ्या frequency "zones" किती मोठ्या आवाजाच्या आहेत हे मोजते
- animation अधिक smooth करताना responsiveness टिकवून ठेवण्यासाठी **Downward-only smoothing** लागू केले जाते (हे secret sauce आहे)
- मग ते glyphs वर प्रदर्शित होण्यास तयार होते!

## 🛠️ प्रीसेट्स
व्हिज्युलायझरचे वर्तन, फ्रिक्वेन्सी रेंजपासून ते ॲनिमेशन स्मूथिंगपर्यंत, पूर्णपणे `zones.config` फाईलद्वारे नियंत्रित केले जाते. तुम्हाला विद्यमान प्रीसेट्समध्ये बदल करायचे असोत किंवा नवीन फोन मॉडेलसाठी सपोर्ट जोडायचा असो, तुम्हाला आवश्यक असलेली सर्व माहिती आमच्या कॉन्फिगरेशन गाईडमध्ये मिळेल.

### 📖 [**zones.config चे सविस्तर डॉक्युमेंटेशन**] (ZONES_CONFIG.md)

## 📖 App कसा वापरायचा?
1. **releases मधून नवीनतम APK डाउनलोड करा**.
2. **परवानग्या द्या**: App ला Screen Capture (Media Projection) आणि Notification access आवश्यक आहे.
3. **Visualizing सुरू करा**: "Start" बटण दाबा आणि कोणत्याही app मधून संगीत वाजवा!
4. **Latency समायोजित करा**: जर तुमच्या Bluetooth speaker किंवा headphones सोबत lights व्यवस्थित sync होत नसतील, तर delay जोडण्यासाठी किंवा काढण्यासाठी **Audio** tab वापरा.
5. **Presets बदला**: **Glyphs** tab मध्ये वेगवेगळ्या visualization styles एक्सप्लोर करा आणि तुमच्या आवडीनुसार values बदला!

## 📖 Python script कसा वापरायचा?
वापर अगदी सोपा आणि सरळ आहे. तरीही, आम्ही एक विस्तृत wiki page बनवली आहे जी installation, वापर, configuration files तपशीलवार आणि troubleshooting section स्पष्ट करते. नवीन presets कसे बनवायचे हे देखील तुम्हाला कळू शकते (अजून नाही). [**musicViz.py** Python script म्हणून कसा वापरायचा हे पाहण्यासाठी येथे क्लिक करा](https://github.com/oliver-lebaigue-bright-bench/glyph-syncronator/wiki/). काय छान आहे माहीत आहे? तुम्ही कोणत्याही त्रासाशिवाय bulk मध्ये असंख्य files convert करू शकता!

## <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Hand%20gestures/Handshake.png" alt="Handshake" width="25" height="25" /> आमच्या community मध्ये सामील व्हा
तुम्हाला बोलायचे किंवा चर्चा करायची आहे का? *Bugs, feature requests?* [**Nothing server च्या official discord thread मध्ये सामील होण्यास मोकळ्या मनाने या!**](https://discord.com/channels/930878214237200394/1434923843239280743)

## 🏗️ योगदान
या आणि आम्हाला मदत करा! Contributions खूप स्वागतार्ह आहेत!
तुम्ही करू शकता:
- Issues उघडा
- Pull requests सबमिट करा
- सुधारणा सुचवा
- नवीन visualization ideas वर प्रयोग करा
- नवीन presets बनवा
- Developers सोबत चर्चा करा

##  <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/1f512/512.gif" alt="🔒" width="25" height="25"> सुरक्षा
**VirusTotal scan ची link येथे आढळेल:**  
https://www.virustotal.com/gui/url/c92c1ff82b56eb60bfd1e159592d09f949f0ea2d195e01f7f5adbef0e0b0385b?nocache=1

### <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Symbols/Copyright.png" alt="Copyright" width="25" height="25" /> Credits:
#### या प्रकल्पात सहभागी असलेल्या व्यक्तींची नावे खालीलप्रमाणे आहेत: 
<table>
  <tr>
    <td width="100" align="center">
      <a href="https://github.com/oliver-lebaigue-bright-bench">
        <img src="https://github.com/oliver-lebaigue-bright-bench.png?size=100&mask=circle" width="50" style="border-radius: 50%; border: 2px solid #555;"><br/>
        <sub><b>Oliver Lebaigue</b></sub>
      </a>
    </td>
    <td align="left">
      <strong>Founder, Coordinator & Developer</strong><br/>
      Main idea and owner.
    </td>
  </tr>
  <tr>
    <td width="100" align="center">
      <a href="https://github.com/rKyzen">
        <img src="https://github.com/rKyzen.png?size=100&mask=circle" width="50" style="border-radius: 50%; border: 2px solid #555;"><br/>
        <sub><b>rKyzen</b></sub><br/>
        <i>(Shivank Dan)</i>
      </a>
    </td>
    <td align="left">
      <strong>Android App Developer</strong><br/>
      Implemented the real-time music stream.
    </td>
  </tr>
  <tr>
    <td width="100" align="center">
      <a href="https://github.com/oliver-lebaigue-bright-bench">
        <img src="https://github.com/oliver-lebaigue-bright-bench.png?size=100&mask=circle" width="50" style="border-radius: 50%; border: 2px solid #555;"><br/>
        <sub><b>Oliver Lebaigue</b></sub>
      </a>
    </td>
    <td align="left">
      <strong>Android Developer</strong>
      Enhancing the app + various nice additions
    </td>
  </tr>
  <tr>
    <td width="100" align="center">
      <a href="https://github.com/Nicouschulas">
        <img src="https://github.com/Nicouschulas.png?size=100&mask=circle" width="50" style="border-radius: 50%; border: 2px solid #555;"><br/>
        <sub><b>Nicouschulas</b></sub>
      </a>
    </td>
    <td align="left">
      <strong>Wiki & Documentation</strong><br/>
      Readme & Wiki enhancements.
    </td>
  </tr>
  <tr>
    <td width="100" align="center">
      <a href="https://github.com/SebiAi">
        <img src="https://github.com/SebiAi.png?size=100&mask=circle" width="50" style="border-radius: 50%; border: 2px solid #555;"><br/>
        <sub><b>SebiAi</b></sub>
      </a>
    </td>
    <td align="left">
      <strong>Glyph Specialist</strong><br/>
      Glyphmodder and glyph related help.
    </td>
  </tr>
  <tr>
    <td width="100" align="center">
      <a href="https://github.com/Earendel-lab">
        <img src="https://github.com/Earendel-lab.png?size=100&mask=circle" width="50" style="border-radius: 50%; border: 2px solid #555;"><br/>
        <sub><b>Earendel</b></sub>
      </a>
    </td>
    <td align="left">
      <strong>Documentation</strong><br/>
      Readme enhancements.
    </td>
  </tr>
  <tr>
    <td width="100" align="center">
      <a href="https://github.com/Luke20YT">
        <img src="https://github.com/Luke20YT.png?size=100&mask=circle" width="50" style="border-radius: 50%; border: 2px solid #555;"><br/>
        <sub><b>あけ なるかみ</b></sub><br/>
        <i>(Luke20YT)</i>
      </a>
    </td>
    <td align="left">
      <strong>Integrator</strong><br/>
      Creating a Music app with an integration with this script.
    </td>
  </tr>
  <tr>
    <td width="100" align="center">
      <a href="https://github.com/Interlastic">
        <img src="https://github.com/Interlastic.png?size=100&mask=circle" width="50" style="border-radius: 50%; border: 2px solid #555;"><br/>
        <sub><b>Interlastic</b></sub>
      </a>
    </td>
    <td align="left">
      <strong>Tools</strong><br/>
      Discord Bot to try the script easily (deprecated).
    </td>
  </tr>
</table>

### <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Travel%20and%20places/Star.png" alt="Star" width="25" height="25" />Star History
<a href="https://www.star-history.com/?repos=oliver-lebaigue-bright-bench%2Fglyph-syncronator&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=oliver-lebaigue-bright-bench/glyph-syncronator&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=oliver-lebaigue-bright-bench/glyph-syncronator&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=oliver-lebaigue-bright-bench/glyph-syncronator&type=date&legend=top-left" />
 </picture>
</a>

### <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Objects/Laptop.png" alt="Laptop" width="25" height="25" /> विकसक

 
<table>
  <tr>
    <td width="100" align="center">
      <a href="https://github.com/oliver-lebaigue-bright-bench">
        <img src="https://github.com/oliver-lebaigue-bright-bench.png?size=100&mask=circle" width="50" style="border-radius: 50%; border: 2px solid #555;"><br/>
        <sub><b>Oliver Lebaigue</b></sub>
      </a>
    </td>
    <td align="left">
      <strong>Founder, Coordinator & Developer</strong><br/>
      Main idea and owner.
    </td>
  </tr>
  <tr>
    <td width="100" align="center">
      <a href="https://github.com/rKyzen">
        <img src="https://github.com/rKyzen.png?size=100&mask=circle" width="50" style="border-radius: 50%; border: 2px solid #555;"><br/>
        <sub><b>rKyzen</b></sub><br/>
        <i>(Shivank Dan)</i>
      </a>
    </td>
    <td align="left">
      <strong>Android App Developer</strong><br/>
      Implemented the real-time music stream.
    </td>
  </tr>
  <tr>
    <td width="100" align="center">
      <a href="https://github.com/oliver-lebaigue-bright-bench">
        <img src="https://github.com/oliver-lebaigue-bright-bench.png?size=100&mask=circle" width="50" style="border-radius: 50%; border: 2px solid #555;"><br/>
        <sub><b>Oliver Lebaigue</b></sub>
      </a>
    </td>
    <td align="left">
      <strong>Core Developer</strong>
      Enhancing the app + various nice additions
    </td>
  </tr>
</table>

