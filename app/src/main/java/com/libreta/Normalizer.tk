package com.libreta

import java.text.Normalizer as JNormalizer

object Normalizer {
    private val SMALL = mapOf("cero" to 0,"uno" to 1,"una" to 1,"un" to 1,"dos" to 2,"tres" to 3,"cuatro" to 4,"cinco" to 5,"seis" to 6,"siete" to 7,"ocho" to 8,"nueve" to 9,"diez" to 10,"once" to 11,"doce" to 12,"trece" to 13,"catorce" to 14,"quince" to 15,"dieciséis" to 16,"dieciseis" to 16,"diecisiete" to 17,"dieciocho" to 18,"diecinueve" to 19,"veinte" to 20,"veintiuno" to 21,"veintiún" to 21,"veintidós" to 22,"veintidos" to 22,"veintitrés" to 23,"veintitres" to 23,"veinticuatro" to 24,"veinticinco" to 25,"veintiséis" to 26,"veintiseis" to 26,"veintisiete" to 27,"veintiocho" to 28,"veintinueve" to 29)
    private val TENS = mapOf("treinta" to 30,"cuarenta" to 40,"cincuenta" to 50,"sesenta" to 60,"setenta" to 70,"ochenta" to 80,"noventa" to 90)
    private val HUND = mapOf("cien" to 100,"ciento" to 100,"doscientos" to 200,"trescientos" to 300,"cuatrocientos" to 400,"quinientos" to 500,"seiscientos" to 600,"setecientos" to 700,"ochocientos" to 800,"novecientos" to 900)
    private fun parseNumber(t: List<String>, start: Int): Pair<Int, Int>? {
        var i=start; var value=0
        HUND[t[i]]?.let { value+=it; i++ }
        val tens=TENS[t.getOrNull(i)]
        if(tens!=null){ value+=tens; i++; if(t.getOrNull(i)=="y") { val u=SMALL[t.getOrNull(i+1)]; if(u in 1..9){value+=u!!;i+=2} } }
        else { SMALL[t.getOrNull(i)]?.let { value+=it;i++ } }
        return if(i==start)null else value to (i-start)
    }
    private fun numbers(text:String):String { val t=text.split(Regex("\\s+")).filter(String::isNotEmpty); val out=mutableListOf<String>(); var i=0; while(i<t.size){ val p=parseNumber(t,i); if(p==null){out+=t[i];i++}else{out+=p.first.toString();i+=p.second} }; return out.joinToString(" ") }
    private fun phrase(s:String)=Regex("(?<![\\p{L}\\d])${Regex.escape(s)}(?![\\p{L}\\d])")
    private val phrases=listOf("raíz cuadrada de" to "√","raiz cuadrada de" to "√","raíz cúbica de" to "∛","raiz cubica de" to "∛","raíz de" to "√","raiz de" to "√","al cuadrado" to "²","al cubo" to "³","elevado a la" to "^","elevado a" to "^","fracción de" to "","fraccion de" to "","equis" to "x","abre paréntesis" to "(","abre parentesis" to "(","cierra paréntesis" to ")","cierra parentesis" to ")","pi" to "π")
    private val ops=listOf("dividido entre" to "/","dividido por" to "/","igual a" to "=","más" to "+","mas" to "+","menos" to "-","por" to "×","sobre" to "/","entre" to "/","igual" to "=")
    private fun ends(t:String)=t.lastOrNull()?.let { it.isDigit()||it in ")²³π" }==true || t in setOf("x","y","z")
    private fun starts(t:String)=t.firstOrNull()?.let { it.isDigit()||it in "(√∛π" }==true || t in setOf("x","y","z")
    private fun operators(text:String):String { val t=text.split(Regex("\\s+")).filter(String::isNotEmpty);val out=mutableListOf<String>();var i=0;while(i<t.size){ val match=ops.firstNotNullOfOrNull { (p,s)-> val words=p.split(' '); if(t.drop(i).take(words.size)==words) words.size to s else null }; if(match!=null){val (len,sym)=match; val prev=out.lastOrNull();val next=t.getOrNull(i+len);if(prev!=null&&next!=null&&ends(prev)&&starts(next)){out+=sym;i+=len;continue} };out+=t[i++] };return out.joinToString(" ") }
    private val close=Regex("\\blisto\\W*$",RegexOption.IGNORE_CASE)
    fun closes(text:String)=close.containsMatchIn(text)
    fun base(raw:String)=raw.lowercase().replace(close,"").trim().trim(',', '.', '!', '?', ';', ':')
    fun plain(text:String)=JNormalizer.normalize(text.lowercase(),JNormalizer.Form.NFD).replace(Regex("\\p{Mn}+"),"").replace(Regex("[^\\p{L}\\d]+")," ").trim().replace(Regex("\\s+")," ")
    fun dropLeadingKeyword(text:String,keyword:String):String {
        val kw=plain(keyword)
        if(kw.isEmpty())return text
        val words=text.trim().split(Regex("\\s+"))
        for(n in 1..words.size){
            val head=plain(words.take(n).joinToString(" "))
            if(head==kw)return words.drop(n).joinToString(" ")
            if(head.length>kw.length)break
        }
        return text
    }
    fun symbols(base:String):String { var s=numbers(base.lowercase());for((p,v)in phrases)s=s.replace(phrase(p),v);s=operators(s);return s.replace(Regex("\\s*([/^])\\s*"),"$1").replace(Regex("([√∛])\\s+"),"$1").replace(Regex("\\s+([²³])"),"$1").replace(Regex("\\(\\s+"),"(").replace(Regex("\\s+\\)"),")").replace(Regex("\\s+")," ").trim() }
    fun command(t:String):VoiceCommand?=when(plain(t)){"siguiente","siguiente paso","sigue","adelante"->VoiceCommand.NEXT;"repítelo","repitelo","repite","repetir","repite el paso"->VoiceCommand.REPEAT;"más simple","mas simple","explícalo más simple","explicalo mas simple","más fácil","mas facil"->VoiceCommand.SIMPLER;"nuevo ejercicio","nuevo","borrar"->VoiceCommand.NEW;else->null}
}
