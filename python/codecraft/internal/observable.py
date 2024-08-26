class Observable:
    ...  # TODO:descriptor for e.g. entity property that automatically sends commands to the server when written to

    def __set_name__(self, owner, name):
        ...

    def __get__(self, instance, owner):
        ...

    def __set__(self, instance, value):
        ...

    def __delete__(self, instance):
        ...
