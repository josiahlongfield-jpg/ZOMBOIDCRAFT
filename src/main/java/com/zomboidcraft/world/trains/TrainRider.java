package com.zomboidcraft.world.trains;

/** Mixed into every entity: the train car it's riding in or on, and where that train was when it last moved it. */
public interface TrainRider {
    TrainEntity zomboidcraft$train();

    int zomboidcraft$car();

    double zomboidcraft$trainS();

    void zomboidcraft$ride(TrainEntity train, int car, double s);

    /** How far the train carried it this tick (x, z), so its legs only count its own steps. */
    double[] zomboidcraft$carried();
}
