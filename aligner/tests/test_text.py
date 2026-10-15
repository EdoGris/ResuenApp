from app.text import TimedWord, job_id, line_windows, normalize_lyrics, singable_lines, tokens


def test_normalize_drops_blank_lines_and_trims():
    assert normalize_lyrics("﻿  Hola mundo \r\n\r\n  segunda\n") == "Hola mundo\nsegunda"


def test_job_id_matches_app_vector():
    # Mismo vector que AlignmentClientTest en la app: si cambia uno, cambia el otro.
    audio = "a" * 64
    assert job_id(audio, "Uno\n\n dos ") == job_id(audio.upper(), "Uno\ndos")
    assert job_id(audio, "Uno\ndos") == "1119a76a42d85f4a9d682b1a3cb7f11178dc87119f24f0afb453129c2e128957"


def test_singable_lines_skips_labels():
    text = "[Coro]\nTe quiero\n(x2)\nVerse 1:\n(Oh, yeah)\n"
    assert singable_lines(text) == ["Te quiero", "(Oh, yeah)"]


def test_tokens_strip_accents_and_punctuation():
    assert tokens("¡Canción, corazón!") == ["cancion", "corazon"]
    assert tokens("Don’t stop") == ["don't", "stop"]


def words(*items):
    return [TimedWord(t, s, e) for t, s, e in items]


def test_line_windows_from_transcript():
    lines = ["hola mundo", "adios amigo"]
    heard = words(("hola", 1.0, 1.4), ("mundo", 1.5, 2.0), ("adiós", 5.0, 5.5), ("amigo", 5.6, 6.2))
    w = line_windows(lines, heard, 10.0)
    assert (w[0].start, w[0].end, w[0].matched) == (1.0, 2.0, True)
    assert (w[1].start, w[1].end, w[1].matched) == (5.0, 6.2, True)


def test_unmatched_lines_share_the_gap():
    lines = ["hola mundo", "esto no se oye", "nada de nada", "adios amigo"]
    heard = words(("hola", 1.0, 1.4), ("mundo", 1.5, 2.0), ("adios", 8.0, 8.5), ("amigo", 8.6, 9.0))
    w = line_windows(lines, heard, 10.0)
    assert not w[1].matched and not w[2].matched
    assert w[1].start == 2.0 and abs(w[2].end - 8.0) < 1e-9
    assert w[1].end <= w[2].start


def test_out_of_order_match_is_discarded():
    # La segunda línea "coincide" con algo dicho antes que la primera: se descarta.
    lines = ["primera linea larga aqui", "coro coro"]
    heard = words(("coro", 0.5, 0.8), ("coro", 0.9, 1.2), ("primera", 3.0, 3.2), ("linea", 3.3, 3.5),
                  ("larga", 3.6, 3.8), ("aqui", 3.9, 4.0))
    w = line_windows(lines, heard, 10.0)
    assert w[0].matched and w[0].start == 3.0
    assert w[1].start >= w[0].end
