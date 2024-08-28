import time
from asyncio import TaskGroup

import codecraft

client = codecraft.CCClient("ws://127.0.0.1:6767")


async def main():
    tasks = []
    async with TaskGroup() as tg:
        t = time.perf_counter()
        for i in range(1000):
            tasks.append(tg.create_task(codecraft.send_chat(str(i))))
    print(f"Took {time.perf_counter() - t:.3f}s")
    await client.close()


codecraft.async_main(main())
