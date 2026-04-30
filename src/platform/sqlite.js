import SQLite from 'react-native-sqlite-storage';

SQLite.enablePromise(false);

function normalizeResult(raw) {
  if (!raw) return null;
  if (Array.isArray(raw)) return raw[0] || null;
  return raw;
}

function executeSql(db, sql, params = []) {
  return new Promise((resolve, reject) => {
    db.executeSql(
      sql,
      params,
      (_tx, results) => resolve(normalizeResult(results)),
      (_tx, error) => {
        reject(error);
        return false;
      }
    );
  });
}

function splitStatements(sql) {
  return String(sql || '')
    .split(';')
    .map((stmt) => stmt.trim())
    .filter(Boolean);
}

function attachAsyncApi(db) {
  db.execAsync = async (sql) => {
    const statements = splitStatements(sql);
    for (const statement of statements) {
      await executeSql(db, statement, []);
    }
  };

  db.runAsync = async (sql, ...params) => {
    const result = await executeSql(db, sql, params);
    return {
      insertId: result?.insertId ?? null,
      rowsAffected: result?.rowsAffected ?? 0,
    };
  };

  db.getFirstAsync = async (sql, ...params) => {
    const result = await executeSql(db, sql, params);
    if (!result?.rows?.length) return null;
    return result.rows.item(0);
  };

  db.getAllAsync = async (sql, ...params) => {
    const result = await executeSql(db, sql, params);
    const out = [];
    const length = result?.rows?.length ?? 0;
    for (let i = 0; i < length; i += 1) {
      out.push(result.rows.item(i));
    }
    return out;
  };

  db.withTransactionAsync = async (task) => {
    return new Promise((resolve, reject) => {
      db.transaction(
        (tx) => {
          const txDb = {
            runAsync: async (sql, ...params) => {
              return new Promise((res, rej) => {
                tx.executeSql(
                  sql,
                  params,
                  (_innerTx, results) => {
                    const result = normalizeResult(results);
                    res({ insertId: result?.insertId ?? null, rowsAffected: result?.rowsAffected ?? 0 });
                  },
                  (_innerTx, error) => {
                    rej(error);
                    return false;
                  }
                );
              });
            },
            getFirstAsync: async (sql, ...params) => {
              return new Promise((res, rej) => {
                tx.executeSql(
                  sql,
                  params,
                  (_innerTx, results) => {
                    const result = normalizeResult(results);
                    if (!result?.rows?.length) {
                      res(null);
                      return;
                    }
                    res(result.rows.item(0));
                  },
                  (_innerTx, error) => {
                    rej(error);
                    return false;
                  }
                );
              });
            },
            execAsync: async (sql) => {
              const statements = splitStatements(sql);
              for (const statement of statements) {
                await new Promise((res, rej) => {
                  tx.executeSql(
                    statement,
                    [],
                    () => res(),
                    (_innerTx, error) => {
                      rej(error);
                      return false;
                    }
                  );
                });
              }
            },
          };

          Promise.resolve(task(txDb)).then(resolve).catch(reject);
        },
        reject
      );
    });
  };

  return db;
}

export function openDatabaseAsync(name) {
  return new Promise((resolve, reject) => {
    const db = SQLite.openDatabase(
      { name, location: 'default' },
      () => resolve(attachAsyncApi(db)),
      reject
    );
  });
}

export default {
  openDatabaseAsync,
};
