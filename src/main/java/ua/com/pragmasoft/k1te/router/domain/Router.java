package ua.com.pragmasoft.k1te.router.domain;

public interface Router extends Connector {

  Router registerConnector(Connector connector);

}
