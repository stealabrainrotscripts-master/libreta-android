package com.libreta

import org.junit.Assert.*
import org.junit.Test

class NormalizerTest {
    @Test fun parsesSpanishNumbers(){assertEquals("2 + 2",Normalizer.symbols("dos más dos"));assertEquals("34",Normalizer.symbols("treinta y cuatro"));assertEquals("105",Normalizer.symbols("ciento cinco"))}
    @Test fun convertsMathPhrases(){assertEquals("x²",Normalizer.symbols("equis al cuadrado"));assertEquals("√9",Normalizer.symbols("raíz cuadrada de nueve"));assertEquals("3/4",Normalizer.symbols("fracción de tres sobre cuatro"));assertEquals("(2 + 1)",Normalizer.symbols("abre paréntesis dos más uno cierra paréntesis"))}
    @Test fun onlyConvertsOperatorsBetweenOperands(){assertEquals("tengo más tarea",Normalizer.symbols("tengo más tarea"));assertEquals("2 × 3",Normalizer.symbols("dos por tres"));assertEquals("2 = 2",Normalizer.symbols("dos igual a dos"))}
    @Test fun removesClosingWord(){assertTrue(Normalizer.closes("dos más dos listo"));assertEquals("dos más dos",Normalizer.base("Dos más dos, listo"));assertEquals("",Normalizer.base("listo"))}
    @Test fun dropsOnlyLeadingKeyword(){assertEquals("dos más dos",Normalizer.dropLeadingKeyword("oye libreta dos más dos","oye libreta"));assertEquals("oye libretae dos",Normalizer.dropLeadingKeyword("oye libretae dos","oye libreta"))}
    @Test fun mapsVoiceCommands(){assertEquals(VoiceCommand.NEXT,Normalizer.command("siguiente paso"));assertEquals(VoiceCommand.REPEAT,Normalizer.command("repítelo"));assertEquals(VoiceCommand.SIMPLER,Normalizer.command("más simple"));assertEquals(VoiceCommand.NEW,Normalizer.command("nuevo ejercicio"));assertNull(Normalizer.command("resolver esto"))}
}
