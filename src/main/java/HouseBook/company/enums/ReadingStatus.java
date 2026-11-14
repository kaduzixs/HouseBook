package HouseBook.company.enums;

public enum ReadingStatus {
    WANT_TO_READ,
    READING,
    COMPLETED,
    PAUSED,
    // Compatibilidade com registros gravados por versões anteriores do HouseBook.
    @Deprecated NAO_INICIADO,
    @Deprecated EM_ANDAMENTO,
    @Deprecated LIDO
}