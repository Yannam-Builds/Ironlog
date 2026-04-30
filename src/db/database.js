import { Database } from '@nozbe/watermelondb';
import SQLiteAdapter from '@nozbe/watermelondb/adapters/sqlite';
import { schema } from './schema';
import { migrations } from './migrations';
import Exercise from './models/Exercise';
import ExerciseMuscle from './models/ExerciseMuscle';
import Plan from './models/Plan';
import PlanDay from './models/PlanDay';
import PlanExercise from './models/PlanExercise';
import Workout from './models/Workout';
import WorkoutExercise from './models/WorkoutExercise';
import WorkoutSet from './models/WorkoutSet';
import BodyMeasurement from './models/BodyMeasurement';
import AppSetting from './models/AppSetting';
import ProgressPhoto from './models/ProgressPhoto';

const adapter = new SQLiteAdapter({
  schema,
  migrations,
  dbName: 'ironlog_watermelon',
  onSetUpError: (error) => {
    console.error('WatermelonDB setup error', error);
  },
});

export const database = new Database({
  adapter,
  modelClasses: [
    Exercise,
    ExerciseMuscle,
    Plan,
    PlanDay,
    PlanExercise,
    Workout,
    WorkoutExercise,
    WorkoutSet,
    BodyMeasurement,
    AppSetting,
    ProgressPhoto,
  ],
});
