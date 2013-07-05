## Play 2 sample using asynchronous & non-blocking I/O with postgresql-async

This is a [Play 2.1 scala](http://www.playframework.com/documentation/2.1.x/ScalaHome) CRUD application (based on the [computer database sample](https://github.com/playframework/Play20/tree/master/samples/scala/computer-database))
to demonstrate async, non-blocking database access using the [postgresql-async driver](https://github.com/mauricio/postgresql-async) (instead of [anorm](http://www.playframework.com/documentation/2.1.x/ScalaAnorm), which is used by the original sample).

What's changed to replace anorm with postgresql-async is contained in [commit 764d3e33](commit/764d3e33).

To use this sample with the default configuration (see [conf/application.conf](conf/application.conf)) you need a postgresql database `play-coda-reactive` owned by user `play` with password `play`.

Apart from the async/non-blocking database access this sample demonstrates:

- Achieving, table pagination and CRUD forms.
- Integrating with a CSS framework (Twitter Bootstrap).

Twitter Bootstrap requires a different form layout to the default one that the Play 2.0 form helper generates, so this application also provides an example of integrating a custom form input constructor.