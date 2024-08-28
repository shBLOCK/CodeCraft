import time

import codecraft

codecraft.CCClient("ws://127.0.0.1:6767")

t = time.perf_counter()
for i in range(1000):
    codecraft.send_chat(str(i))
print(f"Took {time.perf_counter() - t:.3f}s")
