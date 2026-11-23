package com.cefrium.sample.engine

import com.cefrium.Cefrium
import com.cefrium.sample.data.UserAgentPreset

object KoKoroScripts {

    fun resolveUserAgent(
        preset: UserAgentPreset,
        desktopOverride: Boolean,
        customUserAgent: String,
    ): String {
        val chromeVer = runCatching { Cefrium.getChromiumVersion() }.getOrDefault("134.0.0.0")
        if (desktopOverride) {
            return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/$chromeVer Safari/537.36"
        }
        return when (preset) {
            UserAgentPreset.CHROME_MOBILE ->
                "Mozilla/5.0 (Linux; Android 15; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/$chromeVer Mobile Safari/537.36"
            UserAgentPreset.CHROME_DESKTOP ->
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/$chromeVer Safari/537.36"
            UserAgentPreset.SAFARI_MOBILE ->
                "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 " +
                    "(KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"
            UserAgentPreset.CUSTOM ->
                customUserAgent.trim().ifBlank {
                    "Mozilla/5.0 (Linux; Android 15; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/$chromeVer Mobile Safari/537.36"
                }
        }
    }

    /**
     * Reports hidden/muted YouTube ads to KoKoro via CefriumBridge.
     */
    const val YT_AD_OBSERVER_JS: String = """
        (function(){
          if(window.__kokoroAdObs)return; window.__kokoroAdObs=1;
          var last=null;
          function send(v){
            try{
              window.cefriumQuery({
                request:'ad:'+(v?1:0),
                persistent:false,
                onSuccess:function(){},
                onFailure:function(){}
              });
            }catch(e){}
          }
          function check(){
            var p=document.querySelector('.html5-video-player');
            var ad=!!(p&&(p.classList.contains('ad-showing')||p.classList.contains('ad-interrupting')));
            if(ad!==last){last=ad;send(ad);}
          }
          var mo=new MutationObserver(check);
          function attach(){
            var p=document.querySelector('.html5-video-player');
            if(p){try{mo.observe(p,{attributes:true,attributeFilter:['class']});}catch(e){}}
            check();
            setTimeout(attach,3000);
          }
          attach();
          setInterval(check,1000);
        })();
    """

    /**
     * Real passive touch/pointer listener optimization for high-Hz WebGL/Canvas gaming sessions.
     */
    const val PASSIVE_TOUCH_BOOST_JS: String = """
        (function(){
          if(window.__kokoroTouchBoost)return; window.__kokoroTouchBoost=1;
          var origAdd=EventTarget.prototype.addEventListener;
          EventTarget.prototype.addEventListener=function(type,fn,opt){
            if(type==='touchmove'||type==='pointermove'||type==='wheel'){
              if(typeof opt==='boolean')opt={capture:opt,passive:true};
              else if(typeof opt==='object'&&opt!==null&&opt.passive===undefined)opt=Object.assign({},opt,{passive:true});
              else if(opt===undefined)opt={passive:true};
            }
            return origAdd.call(this,type,fn,opt);
          };
        })();
    """

    /**
     * Applies crisp page zoom percentage (75..150) cleanly on the active document.
     */
    fun pageZoomJs(percent: Int): String {
        val clamped = percent.coerceIn(75, 150)
        return """
            (function(){
              if(!document||!document.documentElement)return;
              document.documentElement.style.zoom='${clamped}%';
            })();
        """.trimIndent()
    }

    /**
     * Smart fallback dark-surface stylesheet for pages that ignore native prefers-color-scheme.
     */
    fun forceDarkFallbackJs(enabled: Boolean): String = if (enabled) {
        """
            (function(){
              var id='__kokoro_dark_fallback';
              if(document.getElementById(id))return;
              var bg=window.getComputedStyle(document.body||document.documentElement).backgroundColor||'';
              var m=bg.match(/\d+/g);
              if(m&&m.length>=3){
                var lum=(0.299*Number(m[0])+0.587*Number(m[1])+0.114*Number(m[2]));
                if(lum<135)return;
              }
              var s=document.createElement('style');
              s.id=id;
              s.textContent='html{filter:invert(0.92) hue-rotate(180deg) !important;background:#0b0914 !important;}img,video,picture,canvas,svg,[style*="background-image"]{filter:invert(1) hue-rotate(180deg) !important;}';
              (document.head||document.documentElement).appendChild(s);
            })();
        """.trimIndent()
    } else {
        """
            (function(){
              var el=document.getElementById('__kokoro_dark_fallback');
              if(el&&el.parentNode)el.parentNode.removeChild(el);
            })();
        """.trimIndent()
    }

    /**
     * Reversible Samurai Zen Reader Mode: extracts the primary readable article into a calm
     * distraction-free reading layer without destroying the underlying page.
     */
    fun zenReaderJs(enable: Boolean, darkTheme: Boolean): String = if (enable) {
        val bg = if (darkTheme) "#110E1C" else "#F7F4EE"
        val fg = if (darkTheme) "#EFEAF8" else "#1E192B"
        val muted = if (darkTheme) "#A89EC2" else "#5D5470"
        val accent = if (darkTheme) "#F5C542" else "#5B21B6"
        """
            (function(){
              var id='__kokoro_zen_reader';
              var existing=document.getElementById(id);
              if(existing){existing.remove();}
              var root=document.querySelector('article,main,[role="main"]')||document.body;
              var title=(document.querySelector('h1')&&document.querySelector('h1').innerText)||document.title||'Lettura Zen';
              var blocks=[];
              var nodes=root.querySelectorAll('h1,h2,h3,p,blockquote,li');
              for(var i=0;i<nodes.length;i++){
                var t=(nodes[i].innerText||'').trim();
                if(t.length>25||(nodes[i].tagName.startsWith('H')&&t.length>4)){
                  blocks.push({tag:nodes[i].tagName.toLowerCase(),text:t});
                }
              }
              var overlay=document.createElement('div');
              overlay.id=id;
              overlay.style.cssText='position:fixed;inset:0;z-index:2147483647;overflow-y:auto;background:$bg;color:$fg;padding:28px 22px 96px 22px;font-family:Georgia,serif;line-height:1.78;font-size:18px;-webkit-font-smoothing:antialiased;';
              var wrap=document.createElement('div');
              wrap.style.cssText='max-width:680px;margin:0 auto;';
              var badge=document.createElement('div');
              badge.textContent='KOKORO · LETTURA ZEN';
              badge.style.cssText='font-family:sans-serif;font-size:11px;letter-spacing:2px;color:$accent;margin-bottom:14px;font-weight:700;';
              wrap.appendChild(badge);
              var h=document.createElement('h1');
              h.textContent=title;
              h.style.cssText='font-size:26px;line-height:1.28;margin:0 0 18px 0;color:$fg;';
              wrap.appendChild(h);
              var hr=document.createElement('hr');
              hr.style.cssText='border:none;border-top:1px solid $accent;opacity:0.45;margin:0 0 22px 0;';
              wrap.appendChild(hr);
              if(blocks.length===0){
                var p=document.createElement('p');
                p.textContent=(document.body&&document.body.innerText||'').slice(0,8000);
                p.style.cssText='color:$muted;white-space:pre-line;';
                wrap.appendChild(p);
              }else{
                for(var j=0;j<blocks.length&&j<180;j++){
                  var el=document.createElement(blocks[j].tag==='li'?'p':blocks[j].tag);
                  el.textContent=blocks[j].text;
                  el.style.cssText=blocks[j].tag.startsWith('h')?'font-size:21px;margin:24px 0 10px 0;color:$accent;':'margin:0 0 16px 0;';
                  wrap.appendChild(el);
                }
              }
              overlay.appendChild(wrap);
              document.documentElement.appendChild(overlay);
            })();
        """.trimIndent()
    } else {
        """
            (function(){
              var existing=document.getElementById('__kokoro_zen_reader');
              if(existing&&existing.parentNode)existing.parentNode.removeChild(existing);
            })();
        """.trimIndent()
    }
}
