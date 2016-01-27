(ns om-tutorial.G-Remote-Fetch
  (:require-macros [cljs.test :refer [is]])
  (:require [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [devcards.core :as dc :refer-macros [defcard defcard-doc]]))

(def sample-query [{:widget [(with-meta {:people [:person/name]} {:query-root true})]}])
(def sample-server-response {:people [{:person/name "Joe"}]})
(def server-query (:query (om/process-roots sample-query)))
(def rewrite-function (:rewrite (om/process-roots sample-query)))
(def restructured-response (rewrite-function sample-server-response))

(defcard-doc
  "
  # Remote Fetch

  TODO: The notes below are a mishmash of thoughts...they are more notes to myself as placeholders for what to develop here...
  the notes refer to code that is in development in the same app in `src/main`. The remote helper functions can be
  improved to take a form that allows full generalization over fetch such that loading (even lazily, at depth) can be
  written in a simple form. I'm hoping to finish these helpers by Dec 17th...

  ### Remote Fetch

  For each remote that you list in the reconciler (default is just `:remote`), the parser will run with `:target` set
  in the env to that remote.

  In this mode the parser is passing your read functions bits of query, and you are returning bits of query
  (possibly modified). The point is that the remote parse returns the query you want to run on that remote
  (or nothing if you don't have anything to say).

  So, in \"local read mode (target = nil)\" your read functions return *data* for each part of a *result*.

  In remote read mode (target != nil) your read functions return *query fragments* to *retain* as
   parts of the query to send to the server.

  Since the parser is run once for each remote you can gather up *different* queries to send to *each* remote. All
  of them based on the current UI query.

  The reader factory function for parsing I've created lets you supply a map from remote name to reader function,
  so that you can separate your logic out for each of these query parses.

  In remote parsing mode, the parser expects your read functions to return either `{:remote-name true }` or
  a (possibly modified) `{:remote-name AST-node}` (which
  comes in as `:ast` in `env`). Doing recursive parsing on this is a bit of a pain, but is also typically necessary
  so that you can both maintain the structure of the query (which *must* be rooted from your Root component)
  and prune out the bits you don't want.

  The remote read in this example (so far) only wants a list of people. Everything else is client-local. Using the
  parsing helpers in the `om-tutorial.parsing` namespace, this pares down to this:

  ```
  (defn read-remote [env key params]
    (case key
      :widget (p/recurse-remote env key true)
      :people (p/fetch-if-missing env key :make-root)
      :not-remote ; prune everything else from the parse
      )
    )
  ```

  The `recurse-remote` function basically means \"I have to include this node, because it is on the path to real
    remote data, but itself needs nothing from the server\". The `fetch-if-missing` function has quite a bit
  of logic in it, but basically means \"Everything from here down is valid to ask the server about\".

  The `:make-root` flag (which can be boolean or any other keyword, but only has an effect if it is `:make-root` or `true`)
  is used to set up root processing. I'll cover that more later.

  ## Re-rooting Server Queries

  In our tutorial application we have a top-level component that queries for `:widget`. The queries must compose to
  the root of the UI, but we'd really like to not have to send this client-local bit of the query over to the server,
  as it would mean we'd have to have the server understand what every UI is doing structurally.

  To address this your read function can return an AST node which has been expanded to include `:query-root true`. This will cause
  the parser to mark that node as the intended root of the server query.

  Now, when your send function is called, it will be called with two parameters: The remote-keyed query map:

  "
  {:my-server sample-query}
  "
  and a callback function as a second argument that expects to be given the response which must have the state-tree structure:
  "
  {:widget sample-server-response}
  "
  but what you'd like to do is send this to the server:
  "
  server-query
  "
  get this back:
  "
  sample-server-response
  "
  and then transform it to this:
  "
  restructured-response
  "
  before calling the callback to give the data to Om. (Note: The data above is being generated by code in this section's
  file. You can play with it by editing the source.)

  If you've returned `:query-root true` during the parse phase at the `:people` node, then Om will have marked that
  portion of the query with metadata such that you can use `om/process-roots` to strip off (and then re-add) the
  UI-specific query prefix.

  ```
  (defn send [queries callback]
    (let [full-query (:remote queries)
          {:keys [re-rooted-query rewrite]} (om/process-roots full-query)
          server-response (send-to-server re-rooted-query)
          restructured-response (rewrite server-response)]
          (callback restructured-response)))
  ```

  NOTE: As of alpha26 `process-roots` only handles joins as new roots. It will not follow the branches of a union
  nor can it re-root a plain property.
  ")

(defcard-doc "

  ### Server simulation

  The present example has a server simulation (using a 1 second setTimeout). Hitting \"refresh\" will clear the `:people`,
  which will cause the remote logic to trigger. One second later you should see the simulated data I've placed on this
  \"in-browser server\". See `client-remoting` and `simulated-server` in the main project.

  The current code only works if you use lein checkouts and Om alpha 25-snapshot. Once alpha 25+ is out, this
  should be easier.
  ")

(defcard-doc "
  ## Handling Results

  The server will respond to queries as if you'd run them through your local parser... e.g. the remoting
  has the exact form as a local parse: You send a query, it returns a response. The fact that there
  is some network plumbing in the middle just means you have a little more error handling to do.

  However, that isn't the whole story: calling the provided callback to put the returned data into the
  database is often not quite enough.

  The default mechanims of Om understand the basics of the app database, but there are a number
  of application-specific details that you must understand, and possibly handle yourself.

  When you create your application's reconciler, you may supply overrides to the following:

  - `:merge-tree` A function used to take a response from the server and merge it into your app database.
  Defaults to a very naive shallow merge.
  - `:migrate` A function to rewrite tempids that have been reassigned by the server. As of alpha24 this function
  will remove any database data that is not currently in the UI query (e.g. isn't on the screen). This may
  cause undesired chattiness over the network, and possibly other bad behavior if you're doing anything
  a bit non-standard with the app database.
  - `:id-key` If you do use the built-in tempid migration, this config option specifies which key in your app state
  maps is used to hold the DB ID (where tempids will appear).

  If you look in `om-tutorial.core` you'll see an alternate tempid migration function that doesn't need id-key
  and doesn't throw out potential cached app state.

  [Next: Remote Mutation](#!/om_tutorial.H_Remote_Mutation)
  ")


