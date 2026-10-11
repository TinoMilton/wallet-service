CREATE FUNCTION reject_ledger_entry_changes() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'ledger entries cannot be updated or deleted';
END;
$$;

CREATE TRIGGER ledger_entry_immutable
BEFORE UPDATE OR DELETE ON ledger_entry
FOR EACH ROW EXECUTE FUNCTION reject_ledger_entry_changes();
