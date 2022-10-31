package ua.com.pragmasoft.k1te.chat;

public interface Request<T extends Request<T, R>, R extends Response> extends Message {

}
