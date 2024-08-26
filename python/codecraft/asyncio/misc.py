from asyncio import current_task


def set_task_name(name: str):
    current_task().set_name(name)
