# Public Task Plugin — implementation notes

This file covers the parts of the Public Task Plugin that are not configured in the Valtimo user
interface: wiring the plugin into a process, replacing the generated HTML template, and the application
settings a public upload endpoint depends on.

For configuring the plugin itself, see [plugin.md](plugin.md).

## Wiring the plugin into a process

The plugin is designed to be added to an existing user task. Valtimo can only link one action to a task,
and that has to be the form, so the public task is created from a subprocess instead:

1. Add a **Task listener** with a **Create: Expression** to the user task, calling
   `${publicTaskService.startNotifyAssigneeCandidateProcess(task)}`.

   ![example public task process](img/public-task-process.png)

2. That correlates a message which starts a subprocess. Add this subprocess to the implementation.

   ![example create url process](img/create-url-process.png)

3. Link the process link to the **Create Public Task URL** task.
4. Implement a notification function to send the URL to the assignee candidate.

The plugin's controller has three endpoints. The first returns the HTML for the form of the user task,
the second accepts the submission and completes the task, the third accepts an uploaded file.

Examples of both processes are in the plugin repository. This is one way to implement it, not the only
one.

## Public task URL

The base of the URL sent to the assignee candidate is resolved when a link is created, in this order:

1. **URL of this environment** on the plugin configuration. See [plugin.md](plugin.md).
2. `valtimo.url` — a full URL including scheme.
3. `valtimo.app.hostname` — a hostname without scheme; the scheme comes from `valtimo.app.scheme`, which
   defaults to `https`.

| Property               | Environment variable   | Example                      |
|------------------------|------------------------|------------------------------|
| `valtimo.url`          | `VALTIMO_URL`          | `https://my-app.example.com` |
| `valtimo.app.hostname` | `VALTIMO_APP_HOSTNAME` | `my-app.example.com`         |
| `valtimo.app.scheme`   | `VALTIMO_APP_SCHEME`   | `https`                      |

Existing setups based on `VALTIMO_URL` are unchanged; deployments that only configure
`VALTIMO_APP_HOSTNAME`, such as Ritense Cloud applications, generate the URL correctly.

An environment that configures none of the three starts up normally. Creating a link then fails with a
message naming the plugin field, because whether a public task URL can be produced is a property of the
plugin configuration doing it, not of the application — an environment that never creates one has nothing
to configure.

A value without a scheme is read as an `https` address, a trailing `/` is dropped, and anything that is
not an `http` or `https` address is refused: the value ends up in a browser and in whatever sends the
link out.

The base is stored with the public task, so the page a link opens keeps addressing the environment the
applicant reached. A public task created before this was kept falls back to the application setting, and
to a path of its own when there is none — the page only ever calls back to itself.

The public task id is a path segment: `<base>/api/v1/public-task/<publicTaskId>`. Older links carrying
the id as a `publicTaskId` query parameter are still accepted so that URLs already sent out keep working,
but that form is deprecated — an id in the query string ends up in `Referer` headers, proxy logs and
browser history.

## Application settings a public upload endpoint depends on

Three application settings decide what the open upload endpoint accepts. None of them are plugin
settings, and the plugin cannot raise or lower them per task. Their defaults are not what a publicly
reachable upload wants, so **check them before putting upload fields in a public form**:

| Property                                                           | Default | Set it to                                                                  |
|--------------------------------------------------------------------|---------|----------------------------------------------------------------------------|
| `spring.servlet.multipart.max-file-size`                           | `1MB`   | At least the largest **Maximum file size** of any process link. See below. |
| `valtimo.upload.accepted-mime-types`                               | empty   | The types the application accepts at all. Empty means **every** file type. |
| `valtimo.virusscan.clamav.TemporaryResourceStorageService.enabled` | `false` | `true`, with ClamAV configured, for anything reachable from the internet.  |

A file over `spring.servlet.multipart.max-file-size` is refused by the servlet container while the
request is being parsed, before the plugin sees it, and the applicant gets the application's generic
error rather than the plugin's message. A public task is therefore capped at that limit whatever its
process link asks for, and a warning is logged when a task is created that asks for more. Raise
`spring.servlet.multipart.max-request-size` along with it.

`valtimo.upload.accepted-mime-types` is the application-wide floor: **Accepted file types** on a process
link and **File Pattern** on a field can only narrow it, never widen it. A warning is logged at startup
when it is empty — an unauthenticated upload endpoint combined with an unrestricted set of file types is
worth noticing before deployment rather than after.

### Retention

Files wait in temporary resource storage between being uploaded and the form being submitted.
`valtimo.temporaryResourceStorage.retentionInMinutes` decides how long they are kept and defaults to
**60 minutes**. A public form is often opened well after the link was sent and filled in over a longer
period than a task inside Valtimo, so with the default an applicant can lose their attachments before
they press submit. Raise it to cover the time a form may realistically be left open.

## Writing your own template

The generated HTML is an example that implementations are expected to replace. Two things carry over into
a template of your own.

**Keep the form definition in a `<script type="application/json">` data block and read it with
`JSON.parse`, as the example does.** The form definition contains case data, so interpolating it straight
into a script block would let that data inject markup or script into the page.

**Carry the upload half over**, or upload fields will render and then quietly do nothing.

### How uploading works

1. The public page cannot render Valtimo's own upload components — those are Angular components of the
   Valtimo front end. The plugin rewrites them to Form.io's `file` component before the page is sent out.
2. The picker uploads each file to `POST <base>/api/v1/public-task/<publicTaskId>/attachment`, which parks
   it in temporary resource storage and answers with its resource id.
3. That resource id travels back in the submission. When the task is completed, Valtimo raises a
   `TemporaryResourceSubmittedEvent` for it, exactly as for an upload from a task inside Valtimo.

Step 3 is why nothing else in the process has to know about public tasks.

### Template variables

Two extra variables are passed to the template:

| Variable                       | Content                                                                  |
|--------------------------------|--------------------------------------------------------------------------|
| `public_task_attachment_url`   | The URL to upload to, for this public task.                              |
| `max_attachment_size_in_bytes` | The largest file this public task accepts, so the page can say so early. |

### The storage provider

A Form.io storage provider registered under the name `publicTask` is what the rewritten upload components
refer to. Register it before `Formio.createForm` is called:

```javascript
const attachmentUrl = '${public_task_attachment_url?js_string?no_esc}';

Formio.Providers.addProvider('storage', 'publicTask', function () {
    return {
        title: 'Public task',
        name: 'publicTask',
        uploadFile: function (file, fileName, dir, progressCallback, url, options) {
            const body = new FormData();
            body.append('file', file, fileName);
            // Which field the file was chosen in, so the server can apply that field's own limits.
            if (options && typeof options.componentKey === 'string') {
                body.append('componentKey', options.componentKey);
            }
            return fetch(attachmentUrl, {method: 'POST', body: body}).then(function (response) {
                // The response body is the value this file takes in the submission.
                return response.ok ? response.json() : response.text().then(Promise.reject.bind(Promise));
            });
        },
        // Nothing is served back to the applicant, so there is nothing to download or delete.
        downloadFile: function (file) {
            return Promise.resolve(file);
        },
        deleteFile: function () {
            return Promise.resolve();
        }
    };
});
```

Three things about this:

- **Take the upload target from the page, not from the component Form.io passes in.** The form definition
  contains case data, and a definition that could name its own upload URL could make the browser post the
  applicant's file somewhere else. `options.componentKey` is safe, because the plugin replaces the whole
  `options` object of every upload component with one it builds itself.
- **Sending `componentKey` is what makes a field's own Maximum File Size and File Pattern enforceable.** A
  template that leaves it out still works and the limits of the public task still apply, but a field that
  asks for less than the task allows is not held to it.
- **Register the provider with a plain `function`, not an arrow function.** Form.io calls storage
  providers with `new`.

### Styling Form.io's markup

Form.io's markup needs three things it does not bring itself. The example template does all three; a
template that skips them ends up with an upload field that cannot be operated.

| Needed                                                          | Why                                                                                                                                                                                                             |
|-----------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| A style for `.sr-only`                                          | Form.io emits Bootstrap 4's `.sr-only`. Bootstrap 5 renamed it to `.visually-hidden` and no longer styles it, so text meant for a screen reader is shown to everyone — a refused file states its message twice. |
| A glyph for `[ref="removeLink"]` and `[ref="fileStatusRemove"]` | Both remove buttons are drawn as an empty `<i class="fa fa-remove">`. Without Font Awesome, or a glyph of your own, they are invisible and nothing can be removed.                                              |
| Pinning `formio.full.min.js` to a version                       | The points above are properties of Form.io's markup at a given version. The example template pins it and checks its hash.                                                                                       |

## File pattern matching

**File Pattern** is a Form.io setting, enforced again on the server. This plugin deviates from Form.io's
own implementation in three ways:

- The type matched against is the one the content was detected as, not the one the browser reported.
- A pattern that both includes and excludes is read as "one of the includes and none of the excludes".
  Form.io's answer depends on the order the parts are written in.
- `!.exe` refuses those files. Form.io's own construction of `!` makes the field refuse everything.

The type is established from the content, with the file name used only where the content is not specific
enough to decide on its own — a `.csv` is `text/csv` rather than the `text/plain` its bytes are, which is
what makes an accepted type like `text/csv` work at all, while an executable renamed `factuur.pdf` is
still an executable.

## Availability

The form is only rendered while the public task is still available. It is refused once the task has been
submitted through the public form, and once the **TimeToLive** window of the process link has passed.
Showing the form, uploading and submitting all apply the same check.

A submission is refused when it points at a file that was not uploaded for its own case, so a link cannot
be used to attach another case's file.
