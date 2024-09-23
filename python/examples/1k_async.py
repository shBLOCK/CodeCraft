import time
import asyncio
import winloop
from asyncio import TaskGroup

import codecraft

client = codecraft.CCClient("ws://127.0.0.1:6767")


async def main():
    t = time.perf_counter()
    async with TaskGroup() as tg:
        for i in range(1000):
            tg.create_task(codecraft.send_chat(str(i)))
    print(f"Took {time.perf_counter() - t:.3f}s")

    t = time.perf_counter()
    await asyncio.gather(*[codecraft.send_chat(str(i)) for i in range(1000)])
    print(f"Took {time.perf_counter() - t:.3f}s")


winloop.run(main())
