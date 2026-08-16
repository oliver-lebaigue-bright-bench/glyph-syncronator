# This is the python script

## <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/2049_fe0f/512.gif" alt="⁉" width="32" height="32"> What does this do ?
`musicViz.py` takes an audio file (such as `.mp3`, `.m4a`, or `.ogg`), generates a `.nglyph` file containing the Glyph animations, then runs the generated file through [*SebiAi’s GlyphModder*](https://github.com/SebiAi/custom-nothing-glyph-tools/) to create a **better music visualisation on Nothing phones**!
It then outputs a **glyphed OGG** file for playback in *Glyph Composer*, *Glyphify* or other glyph ringtone players. (A proper Nothing glyph music player app is in the works by the way!)

### <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/2699_fe0f/512.gif" alt="⚙" width="25" height="25"> How it works (technically)
- **FFT (Fast Fourier Transform)** is used to analyze frequencies in a **20 ms window** for each **16.666 ms frame** (60 FPS), making the visualization more accurate
- **Frequency ranges** can be defined in `zones.config` and are fully customizable
- The **brightness** of each glyph is defined by the **peak magnitude** found in its assigned frequency range  
  This measures how loud different frequency “zones” are
- **Downward-only smoothing** is applied to make the animation smoother while preserving responsiveness
- A `.nglyph` file is generated containing all brightness data  
  (see the [NGlyph Format](https://github.com/SebiAi/custom-nothing-glyph-tools/blob/main/docs/10_The%20NGlyph%20File%20Format.md))
- **SebiAi’s** `GlyphModder.py` converts the `.nglyph` file into a **glyphed `.ogg` ringtone** playable on **Nothing Phones**, containing both:
  - The audio
  - The synchronized Glyph animation

## 📖 How to use the python script?
The usage is pretty simple and straightforward. Nevertheless, we made a detailed wiki page which explains the installation, usage, configuration files in detail and a troubleshooting section. You can also find out how to make new presets(not yet tho). [Just click here to see how to use **musicViz.py** as a python script](https://github.com/oliver-lebaigue-bright-bench/glyph-syncronator/wiki/). You know what's cool? You can convert an unlimited number of files in bulk without any trouble!
