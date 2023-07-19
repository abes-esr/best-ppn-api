package fr.abes.bestppn.utils;

import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;

public class StatusKafka implements TransactionStatus {
    @Override
    public boolean hasSavepoint() {
        return false;
    }

    @Override
    public void flush() {

    }

    @Override
    public Object createSavepoint() throws TransactionException {
        return null;
    }

    @Override
    public void rollbackToSavepoint(Object savepoint) throws TransactionException {

    }

    @Override
    public void releaseSavepoint(Object savepoint) throws TransactionException {

    }

    @Override
    public boolean isNewTransaction() {
        return false;
    }

    @Override
    public void setRollbackOnly() {

    }

    @Override
    public boolean isRollbackOnly() {
        return false;
    }

    @Override
    public boolean isCompleted() {
        return false;
    }
}
