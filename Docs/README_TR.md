# <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Travel%20and%20places/Fire.png" alt="Fire" width="35" height="35" />Better Nothing Müzik Görselleştirici
<img 
  src="https://img.shields.io/github/downloads/oliver-lebaigue-bright-bench/glyph-syncronator/total?style=for-the-badge&logo=github&label=Total%20app%20downloads%20from%20github:&color=ff0000&labelColor=000000"
  style="height:40px; border-radius:12px;">
## 🌐 Diğer dillerde oku: 
🇮🇳 [हिन्दी](README_HI.md)
🇮🇳 [Marathi](README-MR.md)
🇺🇸 [English](README.md)

## <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Smilies/Partying%20Face.png" alt="Partying Face" width="25" height="25" />  Android uygulaması burada!
Basit bir Python betiğinden güçlü bir Android uygulamasına başarılı bir şekilde geçiş yaptık! Uygulama, **Media Projection** kullanarak cihazınızdaki canlı ses akışını yakalar ve doğrudan gliflerde işler. Bu, Spotify, YouTube Music ve temel olarak diğer tüm uygulamalardan gelen müzikleri manuel işlem yapmadan görselleştirebileceğiniz anlamına gelir! Artık sadece yerel dosyalarla sınırlı değilsiniz!

## <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Smilies/Thinking%20Face.png" alt="Thinking Face" width="25" height="25" />Bu projenin varlık sebebi şu şekildedir:
Pek çok kişi (ben dahil) için Nothing tarafından sunulan *stok Glyph Müzik Görselleştirmesi* rastgeleymiş hissi veriyor. 
Teknik olarak öyle olmasa bile, müziğe verilen görsel tepki çok belirgin değil. Üstelik bu özellik, Glyph Arayüzü'nün tam potansiyelini gerçekten kullanmıyor. İşte bu yüzden kendi müzik görselleştiricimi yaptım.

## <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/2696_fe0f/512.gif" alt="⚖" width="32" height="32"> Stok vs Better Müzik Görselleştirici
| Özellik | Nothing Stok | **Better Müzik Görselleştirici** |
| :--- | :--- | :--- |
| **Işık Seviyeleri** | ~2-bit derinlik (3 ışık seviyesi) | **12-bit derinlik (4096 ışık seviyesi)** |
| **Kare Hızı** | ~25 FPS | **60 FPS** |
| **Hassasiyet** | Rastgele hissettiriyor, senkronizasyonu görmek zor | **Her ışığın yoğunluğunu tam olarak belirlemek için FFT analizi kullanır** |
| **Bölgeler** | Standart, tam fiziksel glifler kullanılır | **Her glif segmenti ve alt bölgesi bağımsız olarak kullanılır ve kontrol edilir** |
| **Görselleştirme Yöntemi** | Sadece gerçek zamanlı | **20 ms'ye kadar düşük gecikmeli gerçek zamanlı veya önceden işlenmiş ses dosyaları** |

## <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/1f3ac/512.gif" alt="🎬" width="40" height=""> [Video demoları ve örnekler](https://github.com/oliver-lebaigue-bright-bench/glyph-syncronator/blob/main/Demo-video-examples.md)

### Aradaki farkı iş başında görün! [**Video demolarımıza kolayca göz atmak için buraya tıklayın!**](https://github.com/oliver-lebaigue-bright-bench/glyph-syncronator/blob/main/Demo-video-examples.md)

## 📲 Desteklenen Nothing Phone Modelleri
**Şu an için bu modeller desteklenmektedir:**
- Nothing phone (1)
  - Uygulama için glif hata ayıklama modunun **AÇIK** olması gerekir; bu işlem şu *ADB komutu* ile ayarlanır: `adb shell settings put global nt_glyph_interface_debug_enable 1`. Nothing bize API anahtarını verdiğinde bu durum düzelecektir.
- Nothing phone (2)
- Nothing phone (2a)
- Nothing phone (2a plus)
- Nothing phone (3a)
- Nothing phone (3a pro)
- Nothing Phone (4a)

**Kısmi destek:**
- Nothing Phone (4a pro)
- Nothing phone (3)


### <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/2699_fe0f/512.gif" alt="⚙" width="25" height="25"> Nasıl çalışır (teknik olarak)
- Yüksek kaliteli bir ses akışı yakalanır
- Görselleştirmeyi daha doğru hale getirmek için her **16,666 ms'lik kare** (60 FPS) başına **20 ms'lik bir pencerede** frekansları analiz etmek üzere **FFT (Hızlı Fourier Dönüşümü)** kullanılır
- Her bir glif bölgesi için **frekans aralıkları** `zones.config` dosyasında tanımlanmıştır ve tamamen özelleştirilebilir
- Her glifin **parlaklığı**, kendisine atanan frekans aralığında bulunan **tepe büyüklüğü (peak magnitude)** tarafından belirlenir
  Bu, farklı frekans "bölgelerinin" ne kadar yüksek olduğunu ölçer
- Tepkiselliği korurken animasyonu daha pürüzsüz hale getirmek için **yalnızca aşağı yönlü yumuşatma (downward-only smoothing)** uygulanır (bu işin gizli sosudur)
- Ardından gliflerde görüntülenmeye hazır hale gelir!

## 📖 Uygulama Nasıl Kullanılır?
1. **En son APK'yı** sürümler (releases) sayfasından indirin.
2. **İzinleri Verin**: Uygulamanın Ekran Yakalama (Media Projection) ve Bildirim erişimine ihtiyacı vardır.
3. **Görselleştirmeyi Başlatın**: "Başlat" (Start) düğmesine basın ve herhangi bir uygulamadan müzik çalın!
4. **Gecikmeyi Ayarlayın**: Işıklar Bluetooth hoparlörünüz veya kulaklığınızla mükemmel şekilde senkronize değilse, gecikme eklemek veya çıkarmak için **Ses** (Audio) sekmesini kullanın.
5. **Hazır Ayarları Değiştirin**: **Glifler** (Glyphs) sekmesindeki farklı görselleştirme stillerini keşfedin ve değerleri isteğinize göre ayarlayın!

## 📖 Python betiği nasıl kullanılır?
Kullanımı oldukça basit ve anlaşılırdır. Yine de; kurulumu, kullanımı, yapılandırma dosyalarını ve sorun giderme bölümünü ayrıntılı olarak açıklayan detaylı bir wiki sayfası hazırladık. Ayrıca nasıl yeni hazır ayarlar (preset) oluşturabileceğinizi de öğrenebilirsiniz (henüz eklenmedi). **musicViz.py** dosyasının Python betiği olarak nasıl kullanılacağını görmek için [buraya tıklamanız yeterlidir](https://github.com/oliver-lebaigue-bright-bench/glyph-syncronator/wiki/). Harika olan ne biliyor musunuz? Sınırsız sayıdaki dosyayı hiçbir sorun yaşamadan toplu olarak dönüştürebilirsiniz!

## <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Hand%20gestures/Handshake.png" alt="Handshake" width="25" height="25" /> Topluluğumuza katılın
Konuşmak veya tartışmak mı istiyorsunuz? *Hatalar, özellik istekleri?* [**Nothing sunucusundaki resmi Discord başlığına katılmaktan çekinmeyin!**](https://discord.com/channels/930878214237200394/1434923843239280743)

## 🏗️ Katkıda Bulunma
Gelin ve bize yardım edin! Katkılarınızdan büyük mutluluk duyarız!
Şunları yapabilirsiniz:
- Hata bildirimleri (issue) açmak
- Pull request göndermek
- İyileştirme önerilerinde bulunmak
- Yeni görselleştirme fikirleri denemek
- Yeni hazır ayarlar (preset) oluşturmak
- Geliştiricilerle fikir alışverişinde bulunmak

##  <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/1f512/512.gif" alt="🔒" width="25" height="25"> Güvenlik
**VirusTotal tarama bağlantısına buradan ulaşabilirsiniz:**  
https://www.virustotal.com/gui/url/c92c1ff82b56eb60bfd1e159592d09f949f0ea2d195e01f7f5adbef0e0b0385b?nocache=1

### <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Symbols/Copyright.png" alt="Copyright" width="25" height="25" /> Emeği Geçenler:
#### Bu projede yer alan kişiler şunlardır:
- [Oliver Lebaigue](https://github.com/oliver-lebaigue-bright-bench) (Kurucu ve koordinatör, ana fikir, sahip)
- [Nicouschulas](https://github.com/Nicouschulas) (Beni oku ve Viki geliştirmeleri)
- [rKyzen(namıdiğer Shivank Dan)](https://github.com/rKyzen) (Gerçek zamanlı müzik akışına sahip Android Uygulama geliştiricisi)
- [Oliver Lebaigue](https://github.com/oliver-lebaigue-bright-bench) (Geliştirici)
- [SebiAi](https://github.com/SebiAi) (Glif modlayıcı ve gliflerle ilgili yardım)
- [Earnedel-lab](https://github.com/Earendel-lab) (Beni oku geliştirmeleri)
- [あけ なるかみ](https://github.com/Luke20YT) (Bu betikle entegre çalışan bir müzik uygulaması hazırlayan geliştirici)
- [Interlastic](https://github.com/Interlastic) (Betiği kolayca denemek için Discord Botu) (Kullanımdan kaldırıldı)

### <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Travel%20and%20places/Star.png" alt="Star" width="25" height="25" /> Yıldız Geçmişi
![Yıldız Geçmişi](https://api.star-history.com/svg?repos=oliver-lebaigue-bright-bench/glyph-syncronator&type=Date)
