Contributing to Lagom documentation
===================================

**Pre-requisite**: a forked, cloned branch of the lagom repository

Build the scala javadocs
------------------------

The scala/javadocs must exist for the links in the API docs to work. To build
the javadocs:

1.  From the root directory, enter `sbt unidoc`.

Build the regular docs
----------------------

A doc build starts an HTTP server to render the doc. View the doc with this URL:
`http://localhost:9000`. When editing, you can see your changes immediately by
refreshing the browser.

To build the docs:

1.  Change to the `/docs` folder and enter `sbt run`.

Available sbt options
---------------------

The following sbt options are available:

-   `run` invoke from the `docs` directory to build the docs and start an HTTP
    server.

-   `unidoc` invoke from the root directory (`lagom`) to build the scala
    javadocs.

-   `test `invoke from the `docs` directory to extract and test doc code
    snippets.

-   `markdownEvaluateSbtFiles` invoke from the docs directory to test the sbt
    code snippets.

-   `markdownValidateDocs` invoke from the `docs` directory to validate internal
    links in the docs and links from docs to javadocs.

-   `markdownValidateExternalLinks` invoke from the `docs` directory to validate
    external links.
