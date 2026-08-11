<#--This is a simple, generated example for testing purposes. It is recommended to create a HTML when implementing-->

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/css/bootstrap.min.css"
          rel="stylesheet" integrity="sha384-sRIl4kxILFvY47J16cr9ZwB07vP4J8+LH7qKQnuqkuIAvNWLzeN8tE5YBujZqJLB"
          crossorigin="anonymous">
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/js/bootstrap.bundle.min.js"
            integrity="sha384-FKyoEForCGlyvwx9Hj09JcYn3nv7wiPVlz7YYwJrWVcXK/BmnVDxM+D2scQbITxI"
            crossorigin="anonymous"></script>
    <title>Public Task - Form Submission Example</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            padding: 20px;
            background-color: #f4f7f6;
        }

        #formio {
            background-color: #ffffff;
            padding: 20px;
            border-radius: 8px;
            box-shadow: 0 0 10px rgba(0, 0, 0, 0.1);
        }

        .formio-component input, .formio-component select, .formio-component textarea, .formio-component button {
            width: 100%;
            padding: 10px;
            margin-top: 5px;
            margin-bottom: 5px;
            border: 1px solid #ccc;
            border-radius: 4px;
        }

        .formio-component button {
            background-color: #007bff;
            color: white;
            border: none;
            cursor: pointer;
        }

        .formio-component button:hover {
            background-color: #0056b3;
        }
    </style>
</head>
<body>
<div id="form" class="d-block"></div>
<div id="result" class="d-none"></div>
<#--
    The form definition is emitted as a data block instead of as a JavaScript literal, so that its content is never
    parsed as script. HTML escaping would be the wrong escaping here - the content of a script element is raw text,
    where entities are not decoded - so the value is marked as no_esc and encoded for this context instead: in JSON a
    '<' can only occur inside a string literal, where "\u003C" means exactly the same thing. Replacing every '<'
    therefore keeps the JSON intact while removing every sequence ("</script", "<!--") that the HTML parser acts on.
-->
<script id="form-io-form" type="application/json">${form_io_form?replace("<", "\\u003C")?no_esc}</script>
<script src="https://cdn.form.io/formiojs/formio.full.min.js"></script>
<script>
    const formContainer = document.getElementById('form');
    const resultContainer = document.getElementById('result');
    const formJson = JSON.parse(document.getElementById('form-io-form').textContent);

    Formio.createForm(formContainer, formJson).then(function (form) {
        form.on('submit', function (submission) {
            console.debug('Form submitted', submission);
            <#-- js_string, not the default HTML escaping: this value sits in a JavaScript string literal. -->
            fetch('${public_task_url?js_string?no_esc}', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(submission.data)
            })
                .then(async response => {
                    console.debug('Response', response);
                    // get response body
                    const data = await response.text();
                    // check for error response
                    if (!response.ok) {
                        // get error message from body or default to response status
                        const error = data || response.status;
                        return Promise.reject(error);
                    }
                    // hide form
                    formContainer.classList.remove('d-block');
                    formContainer.classList.add('d-none');
                    // show submission result
                    resultContainer.innerHTML = data;
                    resultContainer.classList.remove('d-none');
                    resultContainer.classList.add('d-block');
                })
                .catch(error => {
                    console.error('Error:', error)
                });
            // Prevent the default form submission behavior
            return false;
        });
    });
</script>
</body>
</html>
