from app.lrc import to_lrc
from app.models import AlignmentResult, Line, Word


def test_enhanced_lrc_has_word_stamps():
    result = AlignmentResult(
        language="es", durationMs=200_000, model="test", confidence=0.9,
        lines=[
            Line(startMs=12_340, endMs=15_000, text="Hola mundo",
                 words=[Word(startMs=12_340, endMs=12_800, text="Hola"), Word(startMs=12_900, endMs=15_000, text="mundo")]),
            Line(startMs=61_005, endMs=62_000, text="Sin palabras"),
        ],
    )
    lrc = to_lrc(result, "Canción", "Artista")
    assert "[ti:Canción]" in lrc and "[ar:Artista]" in lrc and "[la:es]" in lrc
    assert "[00:12.34]<00:12.34>Hola <00:12.90>mundo" in lrc
    assert "[01:01.00]Sin palabras" in lrc
