const tgURL = "https://api.telegram.org/bot";

export const handler = async (event) => {
    const env = {
        token: process.env.TELEGRAM_BOT_TOKEN,
        apiUrl: process.env.TELEGRAM_WEBHOOK_ENDPOINT,
    };

    try {
        let body;

        switch (event.tf.action) {
            case "create":
            case "update":
                body = await performWebHookAction(env.token, `url=${env.apiUrl}`);
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

async function performWebHookAction(token, queryParams = '') {
    const response = await fetch(`${tgURL}${token}/setWebHook?${queryParams}`);
    const json = await response.json();
    if (json.ok) {
        return `${queryParams? 'Registered' : 'Unregistered'} telegram webhook ${queryParams}`;
    } else {
        throw new Error(json.description);
    }
}


