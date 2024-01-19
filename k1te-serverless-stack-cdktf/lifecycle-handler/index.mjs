const tgURL = "https://api.telegram.org/bot";

export const handler = async (event) => {
    const env = {
        token: process.env.TELEGRAM_BOT_TOKEN,
        secretToken: process.env.TELEGRAM_SECRET_TOKEN,
        allowedUpdates: process.env.TELEGRAM_ALLOWED_UPDATES,
        apiUrl: process.env.TELEGRAM_WEBHOOK_ENDPOINT,
    };

    try {
        let body;

        switch (event.tf.action) {
            case "create":
            case "update":
                body = await performWebHookAction(env.token, env.apiUrl,env.secretToken,env.allowedUpdates);
                break;
            case "delete":
                body = await performWebHookAction(env.token);
                break;
            default:
                return {
                    statusCode: 400,
                    body: "Invalid action",
                };
        }

        return {
            statusCode: 200,
            body: JSON.stringify({message: body}),
        };
    } catch (error) {
        return {
            statusCode: 500,
            body: JSON.stringify({error: error.message}),
        };
    }
};

async function performWebHookAction(token, url = '', secretToken = '', allowedUpdates = '') {
    const form = new FormData();
    if (url) {
        form.append('url', url);
        form.append('secret_token', secretToken);
        form.append('allowed_updates', allowedUpdates);
    }

    const response = await fetch(`${tgURL}${token}/setWebhook`, {
        method: 'POST',
        body: form,
    });
    const json = await response.json();
    if (json.ok) {
        return `${url ? 'Registered' : 'Unregistered'} telegram webhook ${url}`;
    } else {
        throw new Error(json.description);
    }
}


