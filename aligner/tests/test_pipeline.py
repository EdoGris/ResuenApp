from app.pipeline import build_result
from app.text import Window


def test_build_result_interpolates_missing_words():
    lines = ["uno 2 tres", "nada"]
    windows = [Window(1.0, 3.0, True), Window(4.0, 5.0, False)]
    segments = [
        {"words": [{"word": "uno", "start": 1.0, "end": 1.4, "score": 0.9},
                   {"word": "2"},
                   {"word": "tres", "start": 2.0, "end": 2.5, "score": 0.8}]},
        {"words": []},
    ]
    r = build_result(lines, windows, segments, "es", 10.0, "test")
    first, second = r.lines
    assert [w.text for w in first.words] == ["uno", "2", "tres"]
    assert first.words[0].startMs < first.words[1].startMs < first.words[2].startMs
    assert first.startMs == 1000 and first.endMs == 2500 and not first.estimated
    # Sin palabras ubicadas: se reparte la ventana y queda marcada como estimada.
    assert second.estimated and second.startMs == 4000 and second.endMs == 5000
    assert r.confidence == 0.85


def test_lines_never_go_backwards():
    lines = ["a b", "c d"]
    windows = [Window(5.0, 6.0, True), Window(4.0, 4.5, True)]
    segments = [
        {"words": [{"word": "a", "start": 5.0, "end": 5.2, "score": 1}, {"word": "b", "start": 5.3, "end": 5.9, "score": 1}]},
        {"words": [{"word": "c", "start": 4.0, "end": 4.2, "score": 1}, {"word": "d", "start": 4.3, "end": 4.5, "score": 1}]},
    ]
    r = build_result(lines, windows, segments, "es", 10.0, "test")
    assert r.lines[1].startMs >= r.lines[0].startMs
