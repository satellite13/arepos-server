-- Prevent duplicate accounts with emails that differ only in letter casing
DROP INDEX IF EXISTS users_email_upper_idx;
CREATE UNIQUE INDEX users_email_upper_idx ON public.users (upper(email));
