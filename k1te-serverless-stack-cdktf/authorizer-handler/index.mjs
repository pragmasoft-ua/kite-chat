export const handler = async (event) => {
    const secretToken = process.env.TELEGRAM_SECRET_TOKEN

    let response = {
        "isAuthorized": false,
    };

    if (event.headers["x-telegram-bot-api-secret-token"] === secretToken) {
        console.log("allowed");
        response = {
            "isAuthorized": true,
        };
    } else {
        console.error(`Invalid secret token: ${event.headers["x-telegram-bot-api-secret-token"]}`);
    }

    return response;
};
