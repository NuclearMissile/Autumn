const MAGIC_CODE = [
    "ArrowUp", "ArrowUp", "ArrowDown", "ArrowDown",
    "ArrowLeft", "ArrowRight", "ArrowLeft", "ArrowRight",
    "KeyB", "KeyA", "KeyB", "KeyA"
].toString();

const INPUT_TIME_LIMIT = 3000;

Rx.Observable.fromEvent(document, 'keyup')
    .map(e => [e.code, Date.now()])
    .bufferCount(12, 1)
    .subscribe(buffer => {
        let delay = Date.now() - buffer[0][1];
        let inputs = buffer.map(i => i[0]).toString();
        if (delay <= INPUT_TIME_LIMIT && inputs === MAGIC_CODE) {
            alert("Hello Autumn!");
        } else if (inputs === MAGIC_CODE) {
            // alert(`too slow ${delay} > ${INPUT_TIME_LIMIT}.`);
        }
    });

