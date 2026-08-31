# Public Task Plugin

The Public Task Plugin makes a user task available outside Valtimo. Someone who does not have a Valtimo
account receives a link, opens the form in their browser, fills it in and sends it. Valtimo picks the
submission up and completes the task, exactly as if it had been done inside Valtimo.

This is meant for people you need something from but who do not work in Valtimo: an applicant who has to
supply a missing document, a contractor who has to confirm an appointment, a resident who has to answer a
question about their case.

The Public Task Plugin is not a standard part of Valtimo. It is available in the Plugins Repo. More
information can be found [here](/introduction/modules/plugin-introduction.md#plugins-repo).

> Replacing the generated HTML template, or setting the plugin up in a new environment? See
> [developer.md](developer.md).

## Configure the plugin

A plugin configuration is required before the plugin can be used. A general description on how to
configure plugins can be found [here](../configure-plugin.md).

This plugin has no specific configuration properties besides the plugin title.

An example plugin configuration:

![example plugin configuration](img/configure-plugin.png)

## Available actions

A general description on how to create process links can be found
[here](../../process-link/create-process-link.md).

### Create Public Task

**Create Public Task** produces the link that is sent to the person who has to fill in the form. Opening
that link shows the form of the user task; sending it completes the task and stores the data in the case.

An example process link configuration:

![example process link configuration](img/create-public-task-process-link.png)

#### The two required fields

- **AssigneeCandidateContactData** — where to find the contact details of the person who gets the link.
  This is the name of a process variable, written with `pv:` in front of it. How the link is actually
  sent — email, letter, text message — is up to your process; the plugin only produces it.
- **TimeToLive** — how many days the link stays usable. After that the link no longer opens the form.

#### The four optional fields

These only matter when the form contains an upload field. Each one falls back to a sensible default when
left empty, so a form without uploads can ignore all four.

| Field                             | Default          | What it does                                              |
|-----------------------------------|------------------|-----------------------------------------------------------|
| **Maximum number of attachments** | 10               | How many files may be sent through this link.             |
| **Maximum file size**             | 10485760 (10 MB) | How large one file may be, in bytes.                      |
| **Accepted file types**           | empty            | Which file types are allowed, as a comma separated list.  |
| **Metadata**                      | empty            | What every file is filed with. See [Metadata](#metadata). |

The two numbers are filled in with their defaults when the screen opens, so you can read off what a link
will accept without having to look it up. Clearing one is allowed and means the same as leaving it: the
default applies. **Accepted file types** starts empty on purpose — a value there would restrict types for
a process that never asked for it.

Leaving a field empty always means "use the default", never "no limit at all".

The values are read when the link is created and stay with it. Editing the process link afterwards
changes what *new* links accept, not what links you have already sent out accept.

## Uploading documents

Upload fields work in the public form. If the user task's form contains a **Valtimo File Upload**, a
**Documenten API** upload field or a plain Form.io file component, the person opening the link gets a
file picker, and the files they choose end up in the case the same way they would have if the task had
been completed inside Valtimo. Nothing has to be changed in the form itself.

### What you need to add to the process

The plugin puts uploaded files in temporary storage and hands them to the process. Something in the
process has to pick them up and do something with them, otherwise they are removed again after a while.

To store the files in the Documenten API, add a **Store uploaded document** service task of the
Documenten API plugin to your process, exactly as you would for a task inside Valtimo. Whatever already
handles uploads from internal tasks keeps working — it does not need to know the file came from a public
task.

Storing files in the Documenten API also needs [Metadata](#metadata) on the process link.

### Restricting a single upload field

The three limits on the process link apply to the whole public task. An individual upload field can be
stricter, using its own settings in the form builder:

| Form builder setting  | What it does                                                                    |
|-----------------------|---------------------------------------------------------------------------------|
| **Maximum File Size** | The largest file this field takes, for example `2MB`.                           |
| **File Pattern**      | The types this field takes, for example `application/pdf,image/jpeg` or `.pdf`. |

A field can only be stricter than the process link, never more permissive. When the two disagree, the
stricter of the two wins.

Two things are worth knowing about **File Pattern**:

- A pattern that names a type, like `application/pdf`, is checked against what the file actually is, not
  against what it is called. Renaming a file does not get it past this check.
- A pattern that names an extension, like `.pdf`, is a statement about the name only.
- Putting `!` in front excludes instead of includes: `!.exe` refuses those files.

### What happens when a file is refused

The file is not added to the form and the person filling it in is told what their file turned out to be —
for example *A file of type text/csv cannot be uploaded*. They are deliberately not told what the form
*does* accept: that is your configuration, and anyone with the link can reach this page.

The type is worked out from the content of the file, with the name used only as a hint where the content
is not conclusive on its own. A spreadsheet saved as `.csv` is recognised as a CSV; a program renamed to
`factuur.pdf` is still recognised as a program and refused. For file types whose content says nothing
about what they are — `csv`, `json`, `md`, `svg` — the name is what decides, so those are the ones where
the person uploading has to get the extension right.

### Things worth knowing

- **The count is of uploads, not of attachments.** Removing a file from the form does not give its place
  back. Set **Maximum number of attachments** high enough that someone who picks the wrong file and does
  it again does not run out. A file that is refused does not cost a place.
- **The link stops working when the task is done.** Once the form has been sent, or the **TimeToLive**
  window has passed, the link no longer opens the form and no more files can be sent through it.
- **Files cannot be moved between cases.** A link can only attach files that were uploaded through that
  same link, for that same case.
- **A form left open for a long time can lose its attachments.** Files wait in temporary storage between
  being uploaded and the form being sent, and that storage is cleared periodically — by default after an
  hour. A public form is often opened days after the link was sent and filled in over a longer stretch
  than an internal task, so if that is likely for your process, ask whoever manages the environment to
  extend it. See [developer.md](developer.md).

## Metadata

**Metadata** on the **Create Public Task** process link is a set of key/value pairs written on every file
uploaded through that link. The plugin only stores files, it does not interpret them, so the keys are not
a fixed list — they are whatever the thing that picks the file up expects to read.

For the Documenten API that thing is the **Store uploaded document** action, which builds the whole
document out of this metadata. It fills in `auteur`, `taal` and `creatiedatum` by itself, but it has
nothing to fall back on for these two, so an upload without them is refused:

| Key                    | What to put there                                     |
|------------------------|-------------------------------------------------------|
| `informatieobjecttype` | The URL of the informatieobjecttype in the catalogus. |
| `titel`                | The title the document gets.                          |

Other keys the Documenten API action reads are `beschrijving`, `vertrouwelijkheidaanduiding`, `status`,
`taal`, `auteur`, `creatiedatum`, `ontvangstdatum` and `verzenddatum`.

Values can refer to process variables and case data, so the metadata can follow the case instead of being
the same for every link:

| Key                    | Value                                    |
|------------------------|------------------------------------------|
| `informatieobjecttype` | `${pv:informatieobjecttypeUrl}`          |
| `titel`                | `Bijlage bij ${doc:/aanvraag/onderwerp}` |
| `status`               | `definitief`                             |

This is configuration, not something the person filling in the form provides — the public form has no
field for it. Every file that arrives through the link is filed the same way.

Like the other fields, the metadata is read when the link is created, so editing the process link
afterwards does not change how files from links you have already sent are filed.

A few keys are set by the plugin itself and cannot be configured: `filename`, `contentType`, `user`,
`documentId`, `filePath`, `fileSize` and `virusScanResult`. Entering them has no effect, and a warning is
logged so it does not look like they were applied.

## Audit

When a public task is completed, this is logged as a **Public Task**.
